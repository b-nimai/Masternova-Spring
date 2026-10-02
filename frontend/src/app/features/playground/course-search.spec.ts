import { Observable } from 'rxjs';
import { TestScheduler } from 'rxjs/testing';
import { CourseSummary } from './course-catalog';
import { searchStates, SearchState } from './course-search';

/**
 * MARBLE TESTS. Each character is 1 ms of VIRTUAL time: "-" = nothing, a letter = a value,
 * "#" = error, "|" = complete, "16ms" = jump ahead. Inside scheduler.run(), debounceTime uses
 * virtual time, so these tests are exact and take no real time.
 */
describe('searchStates (RxJS typeahead pipeline)', () => {
  const java: CourseSummary = {
    id: 'c2',
    title: 'Java',
    category: 'Backend',
    priceMinor: 1,
    rating: 5,
  };
  let scheduler: TestScheduler;

  beforeEach(() => {
    scheduler = new TestScheduler((actual, expected) => expect(actual).toEqual(expected));
  });

  it('debounces: only the last term of a fast burst is searched', () => {
    scheduler.run(({ cold, hot, expectObservable, flush }) => {
      const searched: string[] = [];
      const search = (term: string): Observable<CourseSummary[]> => {
        searched.push(term);
        return cold('--r|', { r: [java] }); // the "server" answers 2 ms after the request
      };
      // typed 2 ms apart: j(0) ja(2) jav(4) java(6), then a pause. Debounce = 10 ms → fires at 16.
      const query$ = hot('a-b-c-d', { a: 'j', b: 'ja', c: 'jav', d: 'java' });

      expectObservable(searchStates(query$, search, 10)).toBe('16ms l-r', {
        l: { kind: 'loading', term: 'java' } satisfies SearchState,
        r: { kind: 'ok', term: 'java', items: [java] } satisfies SearchState,
      });
      flush();
      expect(searched).toEqual(['java']); // ⭐ one request, not four
    });
  });

  it('switchMap cancels an outdated search so old results never win', () => {
    scheduler.run(({ cold, hot, expectObservable }) => {
      // "java" is slow (10 ms); "spring" is fast (1 ms) and is typed while java is still in flight
      const search = (term: string) =>
        term === 'java'
          ? cold('----------r|', { r: [java] })
          : cold('-r|', { r: [] as CourseSummary[] });
      const query$ = hot('a---b', { a: 'java', b: 'spring' });

      // java debounced → loading at 1; spring debounced at 5 → java CANCELLED; spring's answer at 6
      expectObservable(searchStates(query$, search, 1)).toBe('-l---mn', {
        l: { kind: 'loading', term: 'java' },
        m: { kind: 'loading', term: 'spring' },
        n: { kind: 'ok', term: 'spring', items: [] },
        // ⭐ there is no { kind: 'ok', term: 'java' } — unsubscribed when "spring" arrived
      });
    });
  });

  it('an error becomes a state, and the search box keeps working afterwards', () => {
    scheduler.run(({ cold, hot, expectObservable }) => {
      const search = (term: string) =>
        term === 'error'
          ? cold<CourseSummary[]>('-#', {}, new Error('Search is down'))
          : cold('-r|', { r: [java] });
      const query$ = hot('a---b', { a: 'error', b: 'java' });

      expectObservable(searchStates(query$, search, 1)).toBe('-le--mr', {
        l: { kind: 'loading', term: 'error' },
        e: { kind: 'error', term: 'error', message: 'Search is down' },
        m: { kind: 'loading', term: 'java' }, // ⭐ the stream survived the error
        r: { kind: 'ok', term: 'java', items: [java] },
      });
    });
  });

  it('terms shorter than two characters are idle, and repeats are ignored', () => {
    scheduler.run(({ hot, expectObservable }) => {
      const search = (): Observable<CourseSummary[]> => {
        throw new Error('must not search');
      };
      const query$ = hot('a-b', { a: 'j', b: ' j ' }); // trimmed → same term → distinctUntilChanged

      expectObservable(searchStates(query$, search, 1)).toBe('-i', { i: { kind: 'idle' } });
    });
  });
});
