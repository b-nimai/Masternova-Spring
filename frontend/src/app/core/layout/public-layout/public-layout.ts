import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { SiteFooter } from '../site-footer/site-footer';

/**
 * The layout of the public pages (home, catalog, course, auth): the page, then the footer.
 * ⭐ A layout ROUTE: the parent route renders this component and its children fill the outlet, so
 * pages never include the footer themselves. The footer is `@defer (on viewport)`: its code and
 * category request wait until someone scrolls to it.
 */
@Component({
  selector: 'app-public-layout',
  imports: [RouterOutlet, SiteFooter],
  templateUrl: './public-layout.html',
  styleUrl: './public-layout.scss',
})
export class PublicLayout {}
