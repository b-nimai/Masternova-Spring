import { inject, Service, signal } from '@angular/core';
import { Title } from '@angular/platform-browser';
import { ActivatedRouteSnapshot, RouterStateSnapshot, TitleStrategy } from '@angular/router';

/**
 * ⭐ One source for a page's name: each route's `title`. The router hands every navigation to the
 * TitleStrategy, which sets the browser tab ("Cart · Masternova") and publishes the name as a
 * signal that the dashboard header shows above the page.
 *
 * A route with `data: { pageHeader: false }` keeps the tab title but draws its own heading (the
 * course wizard shows the course's name, the payment result its outcome).
 */
@Service()
export class PageTitle extends TitleStrategy {
  private readonly documentTitle = inject(Title);
  private readonly titleState = signal<string | undefined>(undefined);
  private readonly headerState = signal(true);

  readonly title = this.titleState.asReadonly();
  /** Whether the dashboard header should show `title`. */
  readonly showHeader = this.headerState.asReadonly();

  override updateTitle(snapshot: RouterStateSnapshot): void {
    const title = this.buildTitle(snapshot);
    this.titleState.set(title);
    this.headerState.set(deepest(snapshot.root).data['pageHeader'] !== false);
    this.documentTitle.setTitle(title ? `${title} · Masternova` : 'Masternova');
  }
}

function deepest(route: ActivatedRouteSnapshot): ActivatedRouteSnapshot {
  return route.firstChild ? deepest(route.firstChild) : route;
}
