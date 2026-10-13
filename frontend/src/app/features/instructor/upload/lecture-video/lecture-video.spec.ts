import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Subject } from 'rxjs';
import { ChunkedUploader, UploadProgress } from '../chunked-uploader';
import { LectureVideo } from './lecture-video';

describe('LectureVideo', () => {
  let fixture: ComponentFixture<LectureVideo>;
  let http: HttpTestingController;
  let uploads: Subject<UploadProgress>;

  beforeEach(async () => {
    uploads = new Subject<UploadProgress>();
    await TestBed.configureTestingModule({
      imports: [LectureVideo],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: ChunkedUploader, useValue: { upload: () => uploads } },
      ],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(LectureVideo);
    fixture.componentRef.setInput('lectureTitle', 'Pods');
  });

  afterEach(() => http.verify());

  const html = () => (fixture.nativeElement as HTMLElement).textContent ?? '';

  function pickFile(): void {
    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      '[data-testid="video-file-Pods"]',
    )!;
    Object.defineProperty(input, 'files', {
      value: [new File([new Uint8Array(4)], 'pods.mp4', { type: 'video/mp4' })],
    });
    input.dispatchEvent(new Event('change'));
  }

  it('uploads with a progress bar, then hands the asset to the lecture', async () => {
    let attached: string | undefined;
    fixture.componentInstance.uploaded.subscribe((id) => (attached = id));
    await fixture.whenStable();
    expect(html()).toContain('Upload video');

    pickFile();
    uploads.next({ phase: 'uploading', assetId: 'a1', sentBytes: 1, totalBytes: 4 });
    await fixture.whenStable();
    expect(html()).toContain('Uploading 25%');

    uploads.next({ phase: 'done', assetId: 'a1' });
    expect(attached).toBe('a1');
  });

  it('follows its asset over the progress stream until ready', async () => {
    fixture.componentRef.setInput('assetId', 'a1');
    await fixture.whenStable();

    const stream = http.expectOne('/api/v1/media/assets/a1/stream');
    stream.flush(
      'event:snapshot\ndata:{"status":"PROCESSING","percent":40,"failureCode":null,"jobs":[]}\n\n' +
        'event:snapshot\ndata:{"status":"READY","percent":100,"failureCode":null,"jobs":[]}\n\n',
    );
    await fixture.whenStable();

    expect(html()).toContain('Video ready');
  });

  it('explains a failed asset in words, not codes', async () => {
    fixture.componentRef.setInput('assetId', 'a2');
    await fixture.whenStable();

    http
      .expectOne('/api/v1/media/assets/a2/stream')
      .flush(
        'event:snapshot\ndata:{"status":"FAILED","percent":0,"failureCode":"MEDIA_UNREADABLE","jobs":[]}\n\n',
      );
    await fixture.whenStable();

    expect(html()).toContain("isn't a video we can read");
  });

  it('an interrupted upload offers to resume', async () => {
    await fixture.whenStable();
    pickFile();
    uploads.error(new Error('network down'));
    await fixture.whenStable();

    expect(html()).toContain('Pick the same file again to resume');
    expect(html()).toContain('Resume');
  });

  it('stops following when the asset is detached (undo)', async () => {
    fixture.componentRef.setInput('assetId', 'a1');
    await fixture.whenStable();
    const stream = http.expectOne('/api/v1/media/assets/a1/stream');

    fixture.componentRef.setInput('assetId', null);
    await fixture.whenStable();

    expect(stream.cancelled).toBe(true);
    expect(html()).toContain('Upload video');
  });
});
