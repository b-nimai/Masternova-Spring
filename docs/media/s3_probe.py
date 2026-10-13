"""Probe an S3-compatible server for what the Phase 7 browser uploader needs (ADR-0012).

Checks: bucket CORS that exposes ETag, CORS preflight (allowed and foreign origin), presigned
UploadPart from a browser origin, tampered signatures rejected, ListParts (resume asks the
provider, ADR-0014), CompleteMultipartUpload, ranged presigned GET (HLS players), and
ListMultipartUploads + Abort (the orphan sweeper, 7.6).

usage:  docker compose --profile media up -d seaweedfs media-init
        python3 -m venv .venv && .venv/bin/pip install boto3 requests
        .venv/bin/python docs/media/s3_probe.py seaweedfs http://localhost:9010 masternova masternova-dev-secret

Last run: 2026-10-13. The results are in ADR-0012.
"""
import sys, os, json
import boto3, requests
from botocore.config import Config

name, ep, ak, sk = sys.argv[1:5]
ORIGIN = "http://localhost:4200"
B = "masternova-media"
s3 = boto3.client("s3", endpoint_url=ep, aws_access_key_id=ak, aws_secret_access_key=sk,
                  region_name="us-east-1",
                  config=Config(s3={"addressing_style": "path"}, signature_version="s3v4"))
res = {}

def step(key, fn):
    try:
        v = fn()
        res[key] = v if v is not None else "ok"
    except Exception as e:
        res[key] = f"FAIL {type(e).__name__}: {str(e)[:160]}"

step("create_bucket", lambda: s3.create_bucket(Bucket=B) and None)
step("put_bucket_cors", lambda: s3.put_bucket_cors(Bucket=B, CORSConfiguration={"CORSRules": [{
    "AllowedOrigins": [ORIGIN], "AllowedMethods": ["PUT", "GET", "HEAD"],
    "AllowedHeaders": ["*"], "ExposeHeaders": ["ETag"], "MaxAgeSeconds": 600}]}) and None)
step("get_bucket_cors", lambda: json.dumps(s3.get_bucket_cors(Bucket=B)["CORSRules"][0].get("ExposeHeaders")))

key = "uploads/probe/source.mp4"
up = s3.create_multipart_upload(Bucket=B, Key=key, ContentType="video/mp4")["UploadId"]
parts = [os.urandom(5 * 1024 * 1024), os.urandom(1234)]
etags = []
for n, body in enumerate(parts, start=1):
    url = s3.generate_presigned_url("upload_part", Params={"Bucket": B, "Key": key, "UploadId": up,
                                                           "PartNumber": n}, ExpiresIn=600)
    if n == 1:
        pre = requests.options(url, headers={"Origin": ORIGIN, "Access-Control-Request-Method": "PUT",
                                             "Access-Control-Request-Headers": "content-type"})
        res["preflight"] = f"{pre.status_code} allow-origin={pre.headers.get('Access-Control-Allow-Origin')}"
        bad = requests.put(url.replace("X-Amz-Signature=", "X-Amz-Signature=00"), data=b"x")
        res["tampered_signature_rejected"] = bad.status_code in (400, 403)  # AWS: 400 if malformed, 403 if wrong
        evil = requests.options(url, headers={"Origin": "http://evil.example", "Access-Control-Request-Method": "PUT"})
        res["foreign_origin_allowed"] = evil.headers.get("Access-Control-Allow-Origin")
    r = requests.put(url, data=body, headers={"Origin": ORIGIN, "Content-Type": "video/mp4"})
    etags.append(r.headers.get("ETag"))
    if n == 1:
        res["part_put"] = r.status_code
        res["part_etag"] = r.headers.get("ETag")
        res["expose_headers"] = r.headers.get("Access-Control-Expose-Headers")
        res["allow_origin"] = r.headers.get("Access-Control-Allow-Origin")

step("list_parts", lambda: [(p["PartNumber"], p["Size"]) for p in s3.list_parts(Bucket=B, Key=key, UploadId=up).get("Parts", [])])
step("complete", lambda: s3.complete_multipart_upload(Bucket=B, Key=key, UploadId=up, MultipartUpload={
    "Parts": [{"PartNumber": i + 1, "ETag": e} for i, e in enumerate(etags)]}) and None)
step("size_matches", lambda: s3.head_object(Bucket=B, Key=key)["ContentLength"] == sum(map(len, parts)))
get = s3.generate_presigned_url("get_object", Params={"Bucket": B, "Key": key}, ExpiresIn=60)
step("presigned_range_get", lambda: requests.get(get, headers={"Range": "bytes=0-99", "Origin": ORIGIN}).status_code)
up2 = s3.create_multipart_upload(Bucket=B, Key="uploads/probe/abandoned")["UploadId"]
step("list_multipart_uploads", lambda: any(u["UploadId"] == up2 for u in s3.list_multipart_uploads(Bucket=B).get("Uploads", [])))
step("abort", lambda: s3.abort_multipart_upload(Bucket=B, Key="uploads/probe/abandoned", UploadId=up2) and None)

print(json.dumps({name: res}, indent=1))
