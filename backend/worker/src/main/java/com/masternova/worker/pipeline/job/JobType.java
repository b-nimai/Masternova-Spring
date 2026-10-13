package com.masternova.worker.pipeline.job;

/** The four node types of the transcode DAG (video-pipeline LLD §3). */
public enum JobType {
  PROBE,
  TRANSCODE,
  THUMBNAILS,
  PACKAGE
}
