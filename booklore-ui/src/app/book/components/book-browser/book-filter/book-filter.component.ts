import {Component, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {combineLatest, Observable, of, Subject} from 'rxjs';
import {map} from 'rxjs/operators';
import {BookService} from '../../../service/book.service';
import {Library} from '../../../model/library.model';
import {Shelf} from '../../../model/shelf.model';
import {EntityType} from '../book-browser.component';
import {Book} from '../../../model/book.model';
import {Accordion, AccordionContent, AccordionHeader, AccordionPanel} from 'primeng/accordion';
import {AsyncPipe, NgClass, TitleCasePipe} from '@angular/common';
import {Badge} from 'primeng/badge';
import {FormsModule} from '@angular/forms';
import {SelectButton} from 'primeng/selectbutton';

type Filter<T> = { value: T; bookCount: number };

export const ratingRanges = [
  {id: '0to1', label: '0 to 1', min: 0, max: 1},
  {id: '1to2', label: '1 to 2', min: 1, max: 2},
  {id: '2to3', label: '2 to 3', min: 2, max: 3},
  {id: '3to4', label: '3 to 4', min: 3, max: 4},
  {id: '4to4.5', label: '4 to 4.5', min: 4, max: 4.5},
  {id: '4.5plus', label: '4.5+', min: 4.5, max: Infinity}
];

export const fileSizeRanges = [
  {id: '<1mb', label: '< 1 MB', min: 0, max: 1024},
  {id: '1to10mb', label: '1–10 MB', min: 1024, max: 10240},
  {id: '10to50mb', label: '10–50 MB', min: 10240, max: 51200},
  {id: '50to100mb', label: '50–100 MB', min: 51200, max: 102400},
  {id: '250to500mb', label: '250–500 MB', min: 256000, max: 512000},
  {id: '500mbto1gb', label: '500 MB – 1 GB', min: 512000, max: 1048576},
  {id: '1to2gb', label: '1–2 GB', min: 1048576, max: 2097152},
  {id: '5plusgb', label: '5+ GB', min: 5242880, max: Infinity}
];

export const pageCountRanges = [
  {id: '<50', label: '< 50 pages', min: 0, max: 50},
  {id: '50to100', label: '50–100 pages', min: 50, max: 100},
  {id: '100to200', label: '100–200 pages', min: 100, max: 200},
  {id: '200to400', label: '200–400 pages', min: 200, max: 400},
  {id: '400to600', label: '400–600 pages', min: 400, max: 600},
  {id: '600to1000', label: '600–1000 pages', min: 600, max: 1000},
  {id: '1000plus', label: '1000+ pages', min: 1000, max: Infinity}
];

export function isRatingInRange(rating: number | undefined | null, rangeId: string): boolean {
  if (rating == null) return false;
  const range = ratingRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return rating >= range.min && rating < range.max;
}

export function isFileSizeInRange(fileSizeKb: number | undefined, rangeId: string): boolean {
  if (fileSizeKb == null) return false;
  const range = fileSizeRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return fileSizeKb >= range.min && fileSizeKb < range.max;
}

export function isPageCountInRange(pageCount: number | undefined, rangeId: string): boolean {
  if (pageCount == null) return false;
  const range = pageCountRanges.find(r => r.id === rangeId);
  if (!range) return false;
  return pageCount >= range.min && pageCount < range.max;
}

const getLanguageFilter = (book: Book) => {
  const lang = book.metadata?.language;
  return lang ? [{id: lang, name: lang}] : [];
};

function getFileSizeRangeFilters(sizeKb?: number) {
  if (sizeKb == null) return [];
  const match = fileSizeRanges.find(r => sizeKb >= r.min && sizeKb < r.max);
  return match ? [{id: match.id, name: match.label}] : [];
}

function getRatingRangeFilters(rating?: number) {
  if (rating == null) return [];
  const match = ratingRanges.find(r => rating >= r.min && rating < r.max);
  return match ? [{id: match.id, name: match.label}] : [];
}

function extractPublishedYearFilter(book: Book): { id: number; name: string }[] {
  const date = book.metadata?.publishedDate;
  if (!date) return [];
  const year = new Date(date).getFullYear();
  return [{id: year, name: year.toString()}];
}

function getShelfStatusFilter(book: Book): { id: string; name: string }[] {
  const isShelved = book.shelves && book.shelves.length > 0;
  return [{id: isShelved ? 'shelved' : 'unshelved', name: isShelved ? 'Shelved' : 'Unshelved'}];
}

function getPageCountRangeFilters(pageCount?: number) {
  if (pageCount == null) return [];
  const match = pageCountRanges.find(r => pageCount >= r.min && pageCount < r.max);
  return match ? [{id: match.id, name: match.label}] : [];
}

@Component({
  selector: 'app-book-filter',
  templateUrl: './book-filter.component.html',
  styleUrls: ['./book-filter.component.scss'],
  standalone: true,
  imports: [
    Accordion,
    AccordionPanel,
    AccordionHeader,
    AccordionContent,
    NgClass,
    Badge,
    AsyncPipe,
    TitleCasePipe,
    FormsModule,
    SelectButton
  ]
})
export class BookFilterComponent implements OnInit {
  @Output() filterSelected = new EventEmitter<Record<string, any> | null>();
  @Output() filterModeChanged = new EventEmitter<'and' | 'or'>();

  @Input() showFilters: boolean = true;
  @Input() entity$!: Observable<Library | Shelf | null> | undefined;
  @Input() entityType$!: Observable<EntityType> | undefined;
  @Input() resetFilter$!: Subject<void>;

  activeFilters: Record<string, any> = {};
  filterStreams: Record<string, Observable<Filter<any>[]>> = {};
  filterTypes: string[] = [];
  filterModeOptions = [
    {label: 'AND', value: 'and'},
    {label: 'OR', value: 'or'}
  ];
  private _selectedFilterMode: 'and' | 'or' = 'and';
  expandedPanels: number = 0;
  readonly filterLabels: Record<string, string> = {
    author: 'Author',
    category: 'Category',
    series: 'Series',
    publisher: 'Publisher',
    amazonRating: 'Amazon Rating',
    goodreadsRating: 'Goodreads Rating',
    hardcoverRating: 'Hardcover Rating',
    publishedDate: 'Published Year',
    fileSize: 'File Size',
    shelfStatus: 'Shelf Status',
    pageCount: 'Page Count',
    language: 'Language',
  };

  bookService = inject(BookService);

  ngOnInit(): void {
    if (this.entity$ && this.entityType$) {
      this.filterStreams = {
        author: this.getFilterStream((book: Book) => book.metadata?.authors.map(name => ({id: name, name})) || [], 'id', 'name'),
        category: this.getFilterStream((book: Book) => book.metadata?.categories.map(name => ({id: name, name})) || [], 'id', 'name'),
        series: this.getFilterStream((book) => (book.metadata?.seriesName ? [{id: book.metadata.seriesName, name: book.metadata.seriesName}] : []), 'id', 'name'),
        publisher: this.getFilterStream((book) => (book.metadata?.publisher ? [{id: book.metadata.publisher, name: book.metadata.publisher}] : []), 'id', 'name'),
        amazonRating: this.getFilterStream((book: Book) => getRatingRangeFilters(book.metadata?.amazonRating!), 'id', 'name'),
        goodreadsRating: this.getFilterStream((book: Book) => getRatingRangeFilters(book.metadata?.goodreadsRating!), 'id', 'name'),
        hardcoverRating: this.getFilterStream((book: Book) => getRatingRangeFilters(book.metadata?.hardcoverRating!), 'id', 'name'),
        publishedDate: this.getFilterStream(extractPublishedYearFilter, 'id', 'name'),
        fileSize: this.getFilterStream((book: Book) => getFileSizeRangeFilters(book.fileSizeKb), 'id', 'name'),
        pageCount: this.getFilterStream((book: Book) => getPageCountRangeFilters(book.metadata?.pageCount!), 'id', 'name'),
        language: this.getFilterStream(getLanguageFilter, 'id', 'name'),
        shelfStatus: this.getFilterStream(getShelfStatusFilter, 'id', 'name')
      };
      this.filterTypes = Object.keys(this.filterStreams);
    }

    if (this.resetFilter$) {
      this.resetFilter$.subscribe(() => this.clearActiveFilter());
    }

    this.setExpandedPanels();
  }

  private getFilterStream<T>(
    extractor: (book: Book) => T[] | undefined,
    idKey: keyof T,
    nameKey: keyof T
  ): Observable<Filter<T[keyof T]>[]> {
    return combineLatest([this.bookService.bookState$, this.entity$ ?? of(null), this.entityType$ ?? of(EntityType.ALL_BOOKS)]).pipe(
      map(([state, entity, entityType]) => {
        const filteredBooks = this.filterBooksByEntityType(state.books || [], entity, entityType);
        const filterMap = new Map<any, Filter<any>>();
        filteredBooks.forEach((book) => {
          (extractor(book) || []).forEach((item) => {
            const id = item[idKey];
            if (!filterMap.has(id)) {
              filterMap.set(id, {value: item, bookCount: 0});
            }
            filterMap.get(id)!.bookCount += 1;
          });
        });
        return Array.from(filterMap.values()).sort(
          (a, b) =>
            b.bookCount - a.bookCount ||
            a.value[nameKey].toString().localeCompare(b.value[nameKey].toString())
        );
      })
    );
  }

  get selectedFilterMode(): 'and' | 'or' {
    return this._selectedFilterMode;
  }

  set selectedFilterMode(mode: 'and' | 'or') {
    this._selectedFilterMode = mode;
    this.filterModeChanged.emit(mode);
  }

  private filterBooksByEntityType(books: Book[], entity: any, entityType: EntityType): Book[] {
    if (entityType === EntityType.LIBRARY && entity && 'id' in entity) {
      return books.filter((book) => book.libraryId === entity.id);
    }
    if (entityType === EntityType.SHELF && entity && 'id' in entity) {
      return books.filter((book) => book.shelves?.some((shelf) => shelf.id === entity.id));
    }
    return books;
  }

  handleFilterClick(filterType: string, value: any): void {
    if (!this.activeFilters[filterType]) {
      this.activeFilters[filterType] = [];
    }

    const index = this.activeFilters[filterType].indexOf(value);
    if (index > -1) {
      this.activeFilters[filterType].splice(index, 1);
      if (this.activeFilters[filterType].length === 0) {
        delete this.activeFilters[filterType];
      }
    } else {
      this.activeFilters[filterType].push(value);
    }

    if (Object.keys(this.activeFilters).length === 0) {
      this.filterSelected.emit(null);
    } else {
      this.filterSelected.emit({...this.activeFilters});
    }
  }

  setFilters(filters: Record<string, any>) {
    this.activeFilters = {};

    for (const [key, value] of Object.entries(filters)) {
      if (Array.isArray(value)) {
        this.activeFilters[key] = [...value];
      } else {
        this.activeFilters[key] = [value];
      }
    }
    this.filterSelected.emit({...this.activeFilters});
  }

  clearActiveFilter() {
    this.activeFilters = {};
    this.filterSelected.emit(null);
  }

  setExpandedPanels(): void {
    const firstActiveIndex = this.filterTypes.findIndex(
      type => this.activeFilters[type]?.length
    );
    this.expandedPanels = firstActiveIndex !== -1 ? firstActiveIndex : 0;
  }

  onFiltersChanged(): void {
    this.setExpandedPanels();
  }
}
