import { Component } from '@angular/core';
import { RouterOutlet } from '@angular/router';
import { NavBar } from './core/layout/nav-bar/nav-bar';

/**
 * The app shell: the navigation bar above the routed page. Pages sit inside one of two layout
 * routes (app.routes.ts): the public layout (page + footer) or the dashboard layout (sidebar +
 * header + page); the lecture player uses neither.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, NavBar],
  templateUrl: './app.html',
  styleUrl: './app.scss',
})
export class App {}
