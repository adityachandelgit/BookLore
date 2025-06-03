import {AfterViewInit, ChangeDetectorRef, Component, inject, OnInit, ViewChild} from '@angular/core';
import {ActivatedRoute, Router} from '@angular/router';
import {MenuItem, MessageService, PrimeTemplate} from 'primeng/api';
import {LibraryService} from '../../service/library.service';
import {BookService} from '../../service/book.service';
import {debounceTime, filter, map, switchMap, take} from 'rxjs/operators';
import {BehaviorSubject, combineLatest, Observable, of, Subject} from 'rxjs';
import {ShelfService} from '../../service/shelf.service';
import {ShelfAssignerComponent} from '../shelf-assigner/shelf-assigner.component';
import {DialogService, DynamicDialogRef} from 'primeng/dynamicdialog';
import {Library} from '../../model/library.model';
import {Shelf} from '../../model/shelf.model';
import {SortService} from '../../service/sort.service';
import {SortDirection, SortOption} from '../../model/sort.model';
import {BookState} from '../../model/state/book-state.model';
import {Book} from '../../model/book.model';
import {LibraryShelfMenuService} from '../../service/library-shelf-menu.service';
import {BookTableComponent} from './book-table/book-table.component';
import {animate, state, style, transition, trigger} from '@angular/animations';
import {MetadataFetchOptionsComponent} from '../../metadata/metadata-options-dialog/metadata-fetch-options/metadata-fetch-options.component';
import {MetadataRefreshType} from '../../metadata/model/request/metadata-refresh-type.enum';
import {Button} from 'primeng/button';
import {AsyncPipe, NgClass, NgForOf, NgIf, NgStyle} from '@angular/common';
import {VirtualScrollerModule} from '@iharbeck/ngx-virtual-scroller';
import {BookCardComponent} from './book-card/book-card.component';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Menu} from 'primeng/menu';
import {InputText} from 'primeng/inputtext';
import {FormsModule} from '@angular/forms';
import {BookFilterComponent, isFileSizeInRange, isPageCountInRange, isRatingInRange} from './book-filter/book-filter.component';
import {Tooltip} from 'primeng/tooltip';
import {Fluid} from 'primeng/fluid';
import {EntityViewPreferences, UserService} from '../../../settings/user-management/user.service';
import {LockUnlockMetadataDialogComponent} from './lock-unlock-metadata-dialog/lock-unlock-metadata-dialog.component';
import {OverlayPanelModule} from 'primeng/overlaypanel';
import {Slider} from 'primeng/slider';
import {Popover} from 'primeng/popover';

export enum EntityType {
  LIBRARY = 'Library',
  SHELF = 'Shelf',
  ALL_BOOKS = 'All Books',
  UNSHELVED = 'Unshelved Books',
}

const QUERY_PARAMS = {
  VIEW: 'view',
  SORT: 'sort',
  DIRECTION: 'direction',
  FILTER: 'filter'
};

const VIEW_MODES = {
  GRID: 'grid',
  TABLE: 'table',
};

const SORT_DIRECTION = {
  ASCENDING: 'asc',
  DESCENDING: 'desc',
};

@Component({
  selector: 'app-book-browser',
  standalone: true,
  templateUrl: './book-browser.component.html',
  styleUrls: ['./book-browser.component.scss'],
  imports: [Button, NgIf, VirtualScrollerModule, BookCardComponent, AsyncPipe, ProgressSpinner, Menu, NgForOf, InputText, FormsModule, BookTableComponent, BookFilterComponent, Tooltip, NgClass, Fluid, PrimeTemplate, NgStyle, OverlayPanelModule, Slider, Popover],
  animations: [
    trigger('slideInOut', [
      state('void', style({
        transform: 'translateY(100%)'
      })),
      state('*', style({
        transform: 'translateY(0)'
      })),
      transition(':enter', [
        animate('0.1s ease-in')
      ]),
      transition(':leave', [
        animate('0.1s ease-out')
      ])
    ])
  ]
})
export class BookBrowserComponent implements OnInit, AfterViewInit {
  bookState$: Observable<BookState> | undefined;
  entity$: Observable<Library | Shelf | null> | undefined;
  entityType$: Observable<EntityType> | undefined;
  searchTerm$ = new BehaviorSubject<string>('');

  entity: Library | Shelf | null = null;
  entityType: EntityType | undefined;
  bookTitle: string = '';
  entityOptions: MenuItem[] | undefined;
  selectedBooks = new Set<number>();
  isDrawerVisible: boolean = false;
  dynamicDialogRef: DynamicDialogRef | undefined;
  EntityType = EntityType;

  @ViewChild(BookTableComponent) bookTableComponent!: BookTableComponent;
  @ViewChild(BookFilterComponent) bookFilterComponent!: BookFilterComponent;

  selectedFilter = new BehaviorSubject<Record<string, any> | null>(null);
  selectedFilterMode = new BehaviorSubject<'and' | 'or'>('and');
  protected resetFilterSubject = new Subject<void>();

  protected userService = inject(UserService);
  private activatedRoute = inject(ActivatedRoute);
  private messageService = inject(MessageService);
  private libraryService = inject(LibraryService);
  private bookService = inject(BookService);
  private shelfService = inject(ShelfService);
  private dialogService = inject(DialogService);
  private sortService = inject(SortService);
  private router = inject(Router);
  private cdr = inject(ChangeDetectorRef);
  private libraryShelfMenuService = inject(LibraryShelfMenuService);

  sortOptions: any[] = [
    {label: 'Title', icon: '', field: 'title', command: () => this.sortBooks('title')},
    {label: 'Title + Series', icon: '', field: 'titleSeries', command: () => this.sortBooks('titleSeries')},
    {label: 'Author', icon: '', field: 'author', command: () => this.sortBooks('author')},
    {label: 'Last Read', field: 'lastReadTime', command: () => this.sortBooks('lastReadTime')},
    {label: 'Added On', field: 'addedOn', command: () => this.sortBooks('addedOn')},
    {label: 'File Size', icon: '', field: 'fileSizeKb', command: () => this.sortBooks('fileSizeKb')},
    {label: 'Locked', icon: '', field: 'locked', command: () => this.sortBooks('locked')},
    {label: 'Publisher', icon: '', field: 'publisher', command: () => this.sortBooks('publisher')},
    {label: 'Published Date', icon: '', field: 'publishedDate', command: () => this.sortBooks('publishedDate')},
    {label: 'Amazon Rating', icon: '', field: 'amazonRating', command: () => this.sortBooks('amazonRating')},
    {label: 'Amazon #', icon: '', field: 'amazonReviewCount', command: () => this.sortBooks('amazonReviewCount')},
    {label: 'Goodreads Rating', icon: '', field: 'goodreadsRating', command: () => this.sortBooks('goodreadsRating')},
    {label: 'Goodreads #', icon: '', field: 'goodreadsReviewCount', command: () => this.sortBooks('goodreadsReviewCount')},
    {label: 'Hardcover Rating', icon: '', field: 'hardcoverRating', command: () => this.sortBooks('hardcoverRating')},
    {label: 'Hardcover #', icon: '', field: 'hardcoverReviewCount', command: () => this.sortBooks('hardcoverReviewCount')},
    {label: 'Pages', icon: '', field: 'pageCount', command: () => this.sortBooks('pageCount')}
  ];

  selectedSort: SortOption | undefined = undefined;
  currentViewMode: string | undefined = undefined;
  lastAppliedSort: SortOption | null = null;
  filterVisibility = true;

  prefs!: EntityViewPreferences;

  baseWidth = 135;
  baseHeight = 220;
  scaleFactor = 1.0;

  get currentCardSize() {
    return {
      width: Math.round(this.baseWidth * this.scaleFactor),
      height: Math.round(this.baseHeight * this.scaleFactor)
    };
  }

  get gridColumnMinWidth(): string {
    return `${this.currentCardSize.width}px`;
  }

  updateScale(): void {
    this.scaleChange$.next(this.scaleFactor);
  }

  private scaleChange$ = new Subject<number>();

  ngOnInit(): void {

    this.scaleChange$.pipe(debounceTime(1000)).subscribe(scale => {
      const user = this.userService.getCurrentUser();
      if (!user || !this.prefs) return;
      this.prefs.global = this.prefs.global ?? {};
      this.prefs.global.coverSize = scale;
      this.userService.updateUserSetting(user.id, 'entityViewPreferences', this.prefs);
      this.messageService.add({
        severity: 'success',
        summary: 'Cover Size Saved',
        detail: `Cover size set to ${scale.toFixed(2)}x.`
      });
    });

    this.bookService.loadBooks();

    const currentPath = this.activatedRoute.snapshot.routeConfig?.path;

    if (currentPath === 'all-books') {
      this.entityType = EntityType.ALL_BOOKS;
      this.entityType$ = of(EntityType.ALL_BOOKS);
      this.entity$ = of(null);

    } else if (currentPath === 'unshelved-books') {
      this.entityType = EntityType.UNSHELVED;
      this.entityType$ = of(EntityType.UNSHELVED);
      this.entity$ = of(null);

    } else {
      const routeEntityInfo$ = this.getEntityInfoFromRoute();

      this.entity$ = routeEntityInfo$.pipe(
        switchMap(({entityId, entityType}) => this.fetchEntity(entityId, entityType))
      );

      this.entityType$ = routeEntityInfo$.pipe(
        map(({entityType}) => entityType)
      );

      this.entity$?.subscribe((entity) => {
        if (!entity) {
          this.entityOptions = [];
          return;
        }
        if (this.isLibrary(entity)) {
          this.entity = entity;
          this.entityOptions = this.libraryShelfMenuService.initializeLibraryMenuItems(entity);
        } else {
          this.entity = entity;
          this.entityOptions = this.libraryShelfMenuService.initializeShelfMenuItems(entity);
        }
      });
    }

    this.activatedRoute.paramMap.subscribe(() => {
      this.searchTerm$.next('');
      this.bookTitle = '';
      this.deselectAllBooks();
      this.clearFilter();
    });
  }

  private isLibrary(entity: Library | Shelf): entity is Library {
    return (entity as Library).paths !== undefined;
  }

  toggleTableGrid(): void {
    this.currentViewMode = this.currentViewMode === VIEW_MODES.GRID ? VIEW_MODES.TABLE : VIEW_MODES.GRID;
    this.router.navigate([], {
      queryParams: {view: this.currentViewMode},
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  get viewIcon(): string {
    return this.currentViewMode === VIEW_MODES.GRID ? 'pi pi-objects-column' : 'pi pi-table';
  }

  private getEntityInfoFromRoute(): Observable<{ entityId: number; entityType: EntityType }> {
    return this.activatedRoute.paramMap.pipe(
      map(params => {
        const libraryId = Number(params.get('libraryId') || NaN);
        const shelfId = Number(params.get('shelfId') || NaN);
        if (!isNaN(libraryId)) {
          this.entityType = EntityType.LIBRARY;
          return {entityId: libraryId, entityType: EntityType.LIBRARY};
        } else if (!isNaN(shelfId)) {
          this.entityType = EntityType.SHELF;
          return {entityId: shelfId, entityType: EntityType.SHELF};
        } else {
          return {entityId: NaN, entityType: EntityType.ALL_BOOKS};
        }
      })
    );
  }

  private fetchEntity(entityId: number, entityType: EntityType): Observable<Library | Shelf | null> {
    if (entityType == EntityType.LIBRARY) {
      return this.fetchLibrary(entityId);
    } else if (EntityType.SHELF) {
      return this.fetchShelf(entityId);
    }
    return of(null);
  }

  private fetchBooksByEntity(entityId: number, entityType: EntityType): Observable<BookState> {
    if (entityType == EntityType.LIBRARY) {
      return this.fetchBooksByLibrary(entityId);
    } else if (entityType == EntityType.SHELF) {
      return this.fetchBooksByShelf(entityId);
    } else {
      return this.fetchAllBooks();
    }
  }

  private fetchBooksByLibrary(libraryId: number): Observable<BookState> {
    return this.fetchBooks(book => book.libraryId === libraryId);
  }

  private fetchBooksByShelf(shelfId: number): Observable<BookState> {
    return this.fetchBooks(book => {
      return book.shelves?.some(shelf => shelf.id === shelfId) ?? false;
    });
  }

  private fetchAllBooks(): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => this.processBookState(bookState)),
      switchMap(bookState => this.headerFilter(bookState)),
      switchMap(bookState => this.sideBarFilter(bookState))
    );
  }

  private fetchUnshelvedBooks(): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => {
        const unshelvedBooks = (bookState.books || []).filter(book =>
          !book.shelves || book.shelves.length === 0
        );
        return {
          ...bookState,
          books: unshelvedBooks
        };
      }),
      map(bookState => this.processBookState(bookState)),
      switchMap(bookState => this.headerFilter(bookState)),
      switchMap(bookState => this.sideBarFilter(bookState))
    );
  }

  private sideBarFilter(bookState: BookState): Observable<BookState> {
    return combineLatest([this.selectedFilter, this.selectedFilterMode]).pipe(
      map(([activeFilters, mode]) => {
        if (!activeFilters) return bookState;
        const filteredBooks = (bookState.books || []).filter(book => {
          const matches = Object.entries(activeFilters).map(([filterType, filterValues]) => {
            if (!Array.isArray(filterValues) || filterValues.length === 0) {
              return mode === 'or';
            }
            switch (filterType) {
              case 'author':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.authors?.includes(val))
                  : filterValues.some(val => book.metadata?.authors?.includes(val));
              case 'category':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.categories?.includes(val))
                  : filterValues.some(val => book.metadata?.categories?.includes(val));
              case 'publisher':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.publisher === val)
                  : filterValues.some(val => book.metadata?.publisher === val);
              case 'series':
                return mode === 'and'
                  ? filterValues.every(val => book.metadata?.seriesName === val)
                  : filterValues.some(val => book.metadata?.seriesName === val);
              case 'amazonRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.amazonRating, range));
              case 'goodreadsRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.goodreadsRating, range));
              case 'hardcoverRating':
                return filterValues.some(range => isRatingInRange(book.metadata?.hardcoverRating, range));
              case 'publishedDate':
                return filterValues.includes(new Date(book.metadata?.publishedDate || '').getFullYear());
              case 'fileSize':
                return filterValues.some(range => isFileSizeInRange(book.fileSizeKb, range));
              case 'shelfStatus':
                const shelved = book.shelves && book.shelves.length > 0 ? 'shelved' : 'unshelved';
                return filterValues.includes(shelved);
              case 'pageCount':
                return filterValues.some(range => isPageCountInRange(book.metadata?.pageCount!, range));
              case 'language':
                return filterValues.includes(book.metadata?.language);
              default:
                return false;
            }
          });
          return mode === 'and' ? matches.every(m => m) : matches.some(m => m);
        });
        return {...bookState, books: filteredBooks};
      })
    );
  }

  private headerFilter(bookState: BookState): Observable<BookState> {
    return this.searchTerm$.pipe(
      map(term => {
        if (term && term.trim() !== '') {
          const filteredBooks = bookState.books?.filter(book => {
            const matchesTitle = book.metadata?.title?.toLowerCase().includes(term.toLowerCase());
            const matchesSeries = book.metadata?.seriesName?.toLowerCase().includes(term.toLowerCase());
            const matchesAuthor = book.metadata?.authors.some(author =>
              author.toLowerCase().includes(term.toLowerCase())
            );
            return matchesTitle || matchesSeries || matchesAuthor;
          }) || null;
          return {...bookState, books: filteredBooks};
        }
        return bookState;
      })
    );
  }

  private fetchBooks(bookFilter: (book: Book) => boolean): Observable<BookState> {
    return this.bookService.bookState$.pipe(
      map(bookState => {
        if (bookState.loaded && !bookState.error) {
          const filteredBooks = bookState.books?.filter(bookFilter) || [];
          const sortedBooks = this.sortService.applySort(filteredBooks, this.selectedSort!);
          return {...bookState, books: sortedBooks};
        }
        return bookState;
      }),
      switchMap(bookState => this.headerFilter(bookState)),
      switchMap(bookState => this.sideBarFilter(bookState))
    );
  }

  private processBookState(bookState: BookState): BookState {
    if (bookState.loaded && !bookState.error) {
      const sortedBooks = this.sortService.applySort(bookState.books || [], this.selectedSort!);
      return {...bookState, books: sortedBooks};
    }
    return bookState;
  }


  private fetchLibrary(libraryId: number): Observable<Library | null> {
    return this.libraryService.libraryState$.pipe(
      map(libraryState => {
        if (libraryState.libraries) {
          return libraryState.libraries.find(lib => lib.id === libraryId) || null;
        }
        return null;
      })
    );
  }

  private fetchShelf(shelfId: number): Observable<Shelf | null> {
    return this.shelfService.shelfState$.pipe(
      map(shelfState => {
        if (shelfState.shelves) {
          return shelfState.shelves.find(shelf => shelf.id === shelfId) || null;
        }
        return null;
      })
    );
  }

  handleBookSelect(bookId: number, selected: boolean): void {
    if (selected) {
      this.selectedBooks.add(bookId);
    } else {
      this.selectedBooks.delete(bookId);
    }
    this.isDrawerVisible = this.selectedBooks.size > 0;
  }

  onSelectedBooksChange(selectedBookIds: Set<number>): void {
    this.selectedBooks = new Set(selectedBookIds);
    this.isDrawerVisible = this.selectedBooks.size > 0;
  }

  deselectAllBooks(): void {
    this.selectedBooks.clear();
    this.isDrawerVisible = false;
    if (this.bookTableComponent) {
      this.bookTableComponent.clearSelectedBooks();
    }
  }

  unshelfBooks() {
    if (this.entity) {
      this.bookService.updateBookShelves(this.selectedBooks, new Set(), new Set([this.entity.id])).subscribe(
        {
          next: () => {
            this.messageService.add({severity: 'info', summary: 'Success', detail: 'Books shelves updated'});
            this.selectedBooks = new Set<number>();
          },
          error: () => {
            this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update books shelves'});
          }
        }
      );
    }
  }

  applySortOption(sortOption: SortOption): void {
    if (this.entityType === EntityType.ALL_BOOKS) {
      this.bookState$ = this.fetchAllBooks();
    } else if (this.entityType === EntityType.UNSHELVED) {
      this.bookState$ = this.fetchUnshelvedBooks();
    } else {
      const routeParam$ = this.getEntityInfoFromRoute();
      this.bookState$ = routeParam$.pipe(
        switchMap(({entityId, entityType}) => this.fetchBooksByEntity(entityId, entityType))
      );
    }
  }

  onSearchTermChange(term: string): void {
    this.searchTerm$.next(term);
  }

  clearSearch(): void {
    this.bookTitle = '';
    this.onSearchTermChange('');
    this.resetFilters();
  }

  resetFilters() {
    this.resetFilterSubject.next();
  }

  openShelfAssigner() {
    this.dynamicDialogRef = this.dialogService.open(ShelfAssignerComponent, {
      header: `Update Books' Shelves`,
      modal: true,
      closable: true,
      contentStyle: {overflow: 'auto'},
      baseZIndex: 10,
      style: {
        position: 'absolute',
        top: '15%',
      },
      data: {
        isMultiBooks: true,
        bookIds: this.selectedBooks
      },
    });
  }

  updateMetadata() {
    this.dialogService.open(MetadataFetchOptionsComponent, {
      header: 'Metadata Refresh Options',
      modal: true,
      closable: true,
      data: {
        bookIds: Array.from(this.selectedBooks),
        metadataRefreshType: MetadataRefreshType.BOOKS
      }
    })
  }

  toggleFilterSidebar() {
    this.bookFilterComponent.showFilters = !this.bookFilterComponent.showFilters;

    this.router.navigate([], {
      queryParams: {
        [QUERY_PARAMS.FILTER]: this.bookFilterComponent.showFilters.toString()
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  get isFilterActive(): boolean {
    return this.selectedFilter.value !== null;
  }

  clearFilter() {
    if (this.selectedFilter.value !== null) {
      this.selectedFilter.next(null);
    }
    this.clearSearch();
  }

  sortBooks(field: string): void {
    const existingSort = this.sortOptions.find(opt => opt.field === field);
    if (!existingSort) return;

    if (this.selectedSort?.field === field) {
      this.selectedSort = {
        ...this.selectedSort,
        direction: this.selectedSort.direction === SortDirection.ASCENDING
          ? SortDirection.DESCENDING
          : SortDirection.ASCENDING
      };
    } else {
      this.selectedSort = {
        label: existingSort.label,
        field: existingSort.field,
        direction: SortDirection.ASCENDING
      };
    }

    this.updateSortOptions();
    this.applySortOption(this.selectedSort);

    this.router.navigate([], {
      queryParams: {
        sort: this.selectedSort.field,
        direction: this.selectedSort.direction === SortDirection.ASCENDING
          ? SORT_DIRECTION.ASCENDING
          : SORT_DIRECTION.DESCENDING
      },
      queryParamsHandling: 'merge',
      replaceUrl: true
    });
  }

  updateSortOptions() {
    const directionIcon = this.selectedSort!.direction === SortDirection.ASCENDING ? 'pi pi-arrow-up' : 'pi pi-arrow-down';
    this.sortOptions = this.sortOptions.map((option) => ({
      ...option,
      icon: option.field === this.selectedSort!.field ? directionIcon : '',
    }));
  }

  ngAfterViewInit() {
    this.bookFilterComponent.filterSelected.subscribe((filters: Record<string, any> | null) => {
      this.selectedFilter.next(filters);
    });

    this.bookFilterComponent.filterModeChanged.subscribe((mode: 'and' | 'or') => {
      this.selectedFilterMode.next(mode);
    });

    this.activatedRoute.queryParamMap.subscribe(paramMap => {
      const viewParam = paramMap.get(QUERY_PARAMS.VIEW);
      const sortParam = paramMap.get(QUERY_PARAMS.SORT);
      const directionParam = paramMap.get(QUERY_PARAMS.DIRECTION);
      const filterParam = paramMap.get(QUERY_PARAMS.FILTER);

      this.userService.userState$
        .pipe(
          filter((user): user is NonNullable<typeof user> => !!user),
          take(1)
        )
        .subscribe(user => {
          this.prefs = user.userSettings?.entityViewPreferences;
          const globalPrefs = this.prefs?.global;
          const currentEntityTypeStr = this.entityType ? this.entityType.toString().toUpperCase() : undefined;
          this.scaleFactor = user.userSettings.entityViewPreferences?.global?.coverSize ?? 1.0;

          const override = this.prefs?.overrides?.find(o =>
            o.entityType?.toUpperCase() === currentEntityTypeStr &&
            o.entityId === this.entity?.id
          );

          const effectivePrefs = override?.preferences ?? globalPrefs ?? {
            sortKey: 'addedOn',
            sortDir: 'ASC',
            view: 'GRID'
          };

          const userSortKey = effectivePrefs.sortKey;
          const userSortDir = effectivePrefs.sortDir?.toUpperCase() === 'DESC' ? SortDirection.DESCENDING : SortDirection.ASCENDING;

          const matchedSort = this.sortOptions.find(opt => opt.field === sortParam) || this.sortOptions.find(opt => opt.field === userSortKey);

          this.selectedSort = matchedSort ? {
            label: matchedSort.label,
            field: matchedSort.field,
            direction: directionParam ? (directionParam === SORT_DIRECTION.DESCENDING ? SortDirection.DESCENDING : SortDirection.ASCENDING) : userSortDir
          } : {
            label: 'Added On',
            field: 'addedOn',
            direction: SortDirection.DESCENDING
          };

          this.currentViewMode = (viewParam === VIEW_MODES.TABLE || viewParam === VIEW_MODES.GRID) ? viewParam : effectivePrefs.view?.toLowerCase() ?? VIEW_MODES.GRID;
          this.bookFilterComponent.showFilters = filterParam === 'true' || (filterParam === null && this.filterVisibility);
          this.updateSortOptions();

          if (this.lastAppliedSort?.field !== this.selectedSort.field || this.lastAppliedSort?.direction !== this.selectedSort.direction) {
            this.lastAppliedSort = {...this.selectedSort};
            this.applySortOption(this.selectedSort);
          }

          const queryParams: any = {
            [QUERY_PARAMS.VIEW]: this.currentViewMode,
            [QUERY_PARAMS.SORT]: this.selectedSort.field,
            [QUERY_PARAMS.DIRECTION]: this.selectedSort.direction === SortDirection.ASCENDING ? SORT_DIRECTION.ASCENDING : SORT_DIRECTION.DESCENDING,
            [QUERY_PARAMS.FILTER]: this.bookFilterComponent.showFilters.toString()
          };

          const currentParams = this.activatedRoute.snapshot.queryParams;

          if (
            currentParams[QUERY_PARAMS.VIEW] !== queryParams[QUERY_PARAMS.VIEW] ||
            currentParams[QUERY_PARAMS.SORT] !== queryParams[QUERY_PARAMS.SORT] ||
            currentParams[QUERY_PARAMS.DIRECTION] !== queryParams[QUERY_PARAMS.DIRECTION] ||
            currentParams[QUERY_PARAMS.FILTER] !== queryParams[QUERY_PARAMS.FILTER]
          ) {
            this.router.navigate([], {
              queryParams,
              replaceUrl: true
            });
          }

          this.cdr.detectChanges();
        });
    });
  }

  lockUnlockMetadata() {
    const count = this.selectedBooks.size;
    this.dynamicDialogRef = this.dialogService.open(LockUnlockMetadataDialogComponent, {
      header: `Lock or Unlock Metadata for ${count} Selected Book${count > 1 ? 's' : ''}`,
      modal: true,
      closable: true,
      data: {
        bookIds: Array.from(this.selectedBooks)
      }
    });
  }
}
