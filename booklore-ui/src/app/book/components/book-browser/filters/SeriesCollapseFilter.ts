import {BookFilter} from './BookFilter';
import {Observable, BehaviorSubject} from 'rxjs';
import {BookState} from '../../../model/state/book-state.model';
import {map, debounceTime, filter, take} from 'rxjs/operators';
import {Book} from '../../../model/book.model';
import {inject, Injectable} from '@angular/core';
import {MessageService} from 'primeng/api';
import {UserService} from '../../../../settings/user-management/user.service';

@Injectable({providedIn: 'root'})
export class SeriesCollapseFilter implements BookFilter {
  private readonly userService = inject(UserService);
  private readonly messageService = inject(MessageService);

  private readonly seriesCollapseSubject = new BehaviorSubject<boolean>(false);
  readonly seriesCollapse$ = this.seriesCollapseSubject.asObservable();

  private hasUserToggled = false;

  constructor() {
    this.userService.userState$
      .pipe(filter(user => !!user), take(1))
      .subscribe(user => {
        const prefs = user.userSettings?.entityViewPreferences;
        const initialCollapsed = prefs?.global?.seriesCollapsed ?? false;
        this.seriesCollapseSubject.next(initialCollapsed);
      });

    this.seriesCollapse$
      .pipe(debounceTime(500))
      .subscribe(isCollapsed => {
        if (this.hasUserToggled) {
          this.persistCollapsePreference(isCollapsed);
        }
      });
  }

  get isSeriesCollapsed(): boolean {
    return this.seriesCollapseSubject.value;
  }

  setCollapsed(value: boolean): void {
    this.hasUserToggled = true;
    this.seriesCollapseSubject.next(value);
  }

  filter(bookState: BookState): Observable<BookState> {
    return this.seriesCollapse$.pipe(
      map(isCollapsed => {
        if (!isCollapsed || !bookState.books) return bookState;

        const seenSeries = new Set<string>();
        const collapsedBooks: Book[] = [];

        for (const book of bookState.books) {
          const name = book.metadata?.seriesName?.trim();
          if (name && !seenSeries.has(name)) {
            const count = bookState.books.filter(b => b.metadata?.seriesName?.trim() === name).length;
            collapsedBooks.push({
              ...book,
              seriesCount: count,
            });
            seenSeries.add(name);
          } else if (!name) {
            collapsedBooks.push(book);
          }
        }
        return {...bookState, books: collapsedBooks};
      })
    );
  }

  private persistCollapsePreference(isCollapsed: boolean): void {
    const user = this.userService.getCurrentUser();
    const prefs = user?.userSettings?.entityViewPreferences;
    if (!user || !prefs) return;

    prefs.global = prefs.global ?? {};
    prefs.global.seriesCollapsed = isCollapsed;

    this.userService.updateUserSetting(user.id, 'entityViewPreferences', prefs);

    this.messageService.add({
      severity: 'success',
      summary: 'Preference Saved',
      detail: `Series collapse set to ${isCollapsed ? 'enabled' : 'disabled'}.`,
      life: 1500
    });
  }
}
