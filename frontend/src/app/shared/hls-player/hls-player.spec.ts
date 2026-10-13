import { ComponentFixture, TestBed } from '@angular/core/testing';
import { HlsPlayer } from './hls-player';

/** A stand-in for hls.js: records what the component asked of it. */
const created: FakeHls[] = [];
let supported = true;

class FakeHls {
  static Events = { ERROR: 'hlsError' };
  static isSupported = () => supported;
  source = '';
  media: HTMLVideoElement | null = null;
  destroyed = false;
  private handlers: Record<string, (e: string, data: { fatal: boolean; details: string }) => void> =
    {};
  constructor() {
    created.push(this);
  }
  on(event: string, handler: (e: string, data: { fatal: boolean; details: string }) => void) {
    this.handlers[event] = handler;
  }
  loadSource(src: string) {
    this.source = src;
  }
  attachMedia(video: HTMLVideoElement) {
    this.media = video;
  }
  destroy() {
    this.destroyed = true;
  }
  fail(details: string, fatal = true) {
    this.handlers['hlsError']('hlsError', { fatal, details });
  }
}

vi.mock('hls.js', () => ({ default: FakeHls }));

describe('HlsPlayer', () => {
  let fixture: ComponentFixture<HlsPlayer>;

  beforeEach(async () => {
    created.length = 0;
    supported = true;
    await TestBed.configureTestingModule({ imports: [HlsPlayer] }).compileComponents();
    fixture = TestBed.createComponent(HlsPlayer);
    fixture.componentRef.setInput('src', '/api/v1/media/assets/a1/hls/master.m3u8?t=T');
  });

  const settle = async () => {
    await fixture.whenStable();
    await new Promise((r) => setTimeout(r)); // the dynamic import() resolves
  };

  it('plays through hls.js where Media Source Extensions exist', async () => {
    await settle();

    expect(created).toHaveLength(1);
    expect(created[0].source).toBe('/api/v1/media/assets/a1/hls/master.m3u8?t=T');
    expect(created[0].media).toBe(fixture.nativeElement.querySelector('video'));
  });

  it('reports a fatal error, ignores a recoverable one', async () => {
    const errors: string[] = [];
    fixture.componentInstance.failed.subscribe((e) => errors.push(e));
    await settle();

    created[0].fail('fragLoadError', false); // hls.js retries these itself
    created[0].fail('manifestLoadError');

    expect(errors).toEqual(['manifestLoadError']);
  });

  it('stops downloading when it goes away', async () => {
    await settle();
    fixture.destroy();
    expect(created[0].destroyed).toBe(true);
  });

  it('falls back to native HLS (iPhone Safari)', async () => {
    supported = false;
    const original = HTMLMediaElement.prototype.canPlayType;
    HTMLMediaElement.prototype.canPlayType = () => 'maybe';
    try {
      await settle();

      expect(created).toHaveLength(0);
      const video = fixture.nativeElement.querySelector('video') as HTMLVideoElement;
      expect(video.src).toContain('master.m3u8?t=T');
    } finally {
      HTMLMediaElement.prototype.canPlayType = original;
    }
  });
});
