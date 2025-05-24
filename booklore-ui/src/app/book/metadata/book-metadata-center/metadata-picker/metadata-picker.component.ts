import {Component, EventEmitter, inject, Input, OnInit, Output} from '@angular/core';
import {BookMetadata} from '../../../model/book.model';
import {MessageService} from 'primeng/api';
import {Button} from 'primeng/button';
import {FormControl, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {InputText} from 'primeng/inputtext';
import {AsyncPipe, NgClass, NgForOf, NgIf, NgStyle} from '@angular/common';
import {Divider} from 'primeng/divider';
import {BookMetadataCenterService} from '../book-metadata-center.service';
import {Observable} from 'rxjs';
import {Tooltip} from 'primeng/tooltip';
import {UrlHelperService} from '../../../../utilities/service/url-helper.service';
import {BookService} from '../../../service/book.service';
import {Textarea} from 'primeng/textarea';

@Component({
  selector: 'app-metadata-picker',
  standalone: true,
  templateUrl: './metadata-picker.component.html',
  styleUrls: ['./metadata-picker.component.scss'],
  imports: [
    Button,
    FormsModule,
    InputText,
    NgIf,
    Divider,
    ReactiveFormsModule,
    NgClass,
    NgStyle,
    Tooltip,
    AsyncPipe,
    NgForOf,
    Textarea
  ]
})
export class MetadataPickerComponent implements OnInit {

  metadataFieldsTop = [
    {label: 'Title', controlName: 'title', lockedKey: 'titleLocked', fetchedKey: 'title'},
    {label: 'Authors', controlName: 'authors', lockedKey: 'authorsLocked', fetchedKey: 'authors'},
    {label: 'Categories', controlName: 'categories', lockedKey: 'categoriesLocked', fetchedKey: 'categories'},
    {label: 'Publisher', controlName: 'publisher', lockedKey: 'publisherLocked', fetchedKey: 'publisher'},
    {label: 'Published', controlName: 'publishedDate', lockedKey: 'publishedDateLocked', fetchedKey: 'publishedDate'},
    {label: 'ISBN-10', controlName: 'isbn10', lockedKey: 'isbn10Locked', fetchedKey: 'isbn10'},
    {label: 'Language', controlName: 'language', lockedKey: 'languageLocked', fetchedKey: 'language'}
  ];

  metadataDescription = [
    {label: 'Description', controlName: 'description', lockedKey: 'descriptionLocked', fetchedKey: 'description'},
  ];

  metadataFieldsBottom = [
    {label: 'Series', controlName: 'seriesName', lockedKey: 'seriesNameLocked', fetchedKey: 'seriesName'},
    {label: 'Book #', controlName: 'seriesNumber', lockedKey: 'seriesNumberLocked', fetchedKey: 'seriesNumber'},
    {label: 'Total Books', controlName: 'seriesTotal', lockedKey: 'seriesTotalLocked', fetchedKey: 'seriesTotal'},
    {label: 'ISBN-13', controlName: 'isbn13', lockedKey: 'isbn13Locked', fetchedKey: 'isbn13'},
    {label: 'ASIN', controlName: 'asin', lockedKey: 'asinLocked', fetchedKey: 'asin'},
    {label: 'Amazon Reviews', controlName: 'amazonReviewCount', lockedKey: 'amazonReviewCountLocked', fetchedKey: 'amazonReviewCount'},
    {label: 'Amazon Rating', controlName: 'amazonRating', lockedKey: 'amazonRatingLocked', fetchedKey: 'amazonRating'},
    {label: 'Goodreads Reviews', controlName: 'goodreadsReviewCount', lockedKey: 'goodreadsReviewCountLocked', fetchedKey: 'goodreadsReviewCount'},
    {label: 'Goodreads Rating', controlName: 'goodreadsRating', lockedKey: 'goodreadsRatingLocked', fetchedKey: 'goodreadsRating'},
    {label: 'Pages', controlName: 'pageCount', lockedKey: 'pageCountLocked', fetchedKey: 'pageCount'}
  ];

  @Input() fetchedMetadata!: BookMetadata;
  @Output() goBack = new EventEmitter<boolean>();

  metadataForm: FormGroup;
  currentBookId!: number;
  copiedFields: Record<string, boolean> = {};
  savedFields: Record<string, boolean> = {};
  originalMetadata!: BookMetadata;

  private metadataCenterService = inject(BookMetadataCenterService);
  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  protected urlHelper = inject(UrlHelperService);

  bookMetadata$: Observable<BookMetadata | null> = this.metadataCenterService.currentMetadata$;

  constructor() {
    this.metadataForm = new FormGroup({
      title: new FormControl(''),
      subtitle: new FormControl(''),
      authors: new FormControl(''),
      categories: new FormControl(''),
      publisher: new FormControl(''),
      publishedDate: new FormControl(''),
      isbn10: new FormControl(''),
      isbn13: new FormControl(''),
      asin: new FormControl(''),
      description: new FormControl(''),
      pageCount: new FormControl(''),
      language: new FormControl(''),
      amazonRating: new FormControl(''),
      amazonReviewCount: new FormControl(''),
      goodreadsRating: new FormControl(''),
      goodreadsReviewCount: new FormControl(''),
      seriesName: new FormControl(''),
      seriesNumber: new FormControl(''),
      seriesTotal: new FormControl(''),
      thumbnailUrl: new FormControl(''),

      titleLocked: new FormControl(false),
      subtitleLocked: new FormControl(false),
      authorsLocked: new FormControl(false),
      categoriesLocked: new FormControl(false),
      publisherLocked: new FormControl(false),
      publishedDateLocked: new FormControl(false),
      isbn10Locked: new FormControl(false),
      isbn13Locked: new FormControl(false),
      asinLocked: new FormControl(false),
      descriptionLocked: new FormControl(false),
      pageCountLocked: new FormControl(false),
      languageLocked: new FormControl(false),
      amazonRatingLocked: new FormControl(false),
      amazonReviewCountLocked: new FormControl(false),
      goodreadsRatingLocked: new FormControl(false),
      goodreadsReviewCountLocked: new FormControl(false),
      seriesNameLocked: new FormControl(false),
      seriesNumberLocked: new FormControl(false),
      seriesTotalLocked: new FormControl(false),
      thumbnailUrlLocked: new FormControl(false),
    });
  }

  ngOnInit(): void {
    this.bookMetadata$.subscribe((metadata) => {
      if (metadata) {
        this.originalMetadata = metadata;
        this.originalMetadata.thumbnailUrl = this.urlHelper.getCoverUrl(metadata.bookId, metadata.coverUpdatedOn);
        this.currentBookId = metadata.bookId;
        this.metadataForm.patchValue({
          title: metadata.title || null,
          subtitle: metadata.subtitle || null,
          authors: (metadata.authors || []).sort().join(', '),
          categories: (metadata.categories || []).sort().join(', '),
          publisher: metadata.publisher || null,
          publishedDate: metadata.publishedDate || null,
          isbn10: metadata.isbn10 || null,
          isbn13: metadata.isbn13 || null,
          asin: metadata.asin || null,
          description: metadata.description || null,
          pageCount: metadata.pageCount || null,
          language: metadata.language || null,
          amazonRating: metadata.amazonRating || null,
          amazonReviewCount: metadata.amazonReviewCount || null,
          goodreadsRating: metadata.goodreadsRating || null,
          goodreadsReviewCount: metadata.goodreadsReviewCount || null,
          seriesName: metadata.seriesName || null,
          seriesNumber: metadata.seriesNumber || null,
          seriesTotal: metadata.seriesTotal || null,
          thumbnailUrl: this.urlHelper.getCoverUrl(metadata.bookId, metadata.coverUpdatedOn),

          titleLocked: metadata.titleLocked || false,
          subtitleLocked: metadata.subtitleLocked || false,
          authorsLocked: metadata.authorsLocked || false,
          categoriesLocked: metadata.categoriesLocked || false,
          publisherLocked: metadata.publisherLocked || false,
          publishedDateLocked: metadata.publishedDateLocked || false,
          isbn10Locked: metadata.isbn10Locked || false,
          isbn13Locked: metadata.isbn13Locked || false,
          asinLocked: metadata.asinLocked || false,
          descriptionLocked: metadata.descriptionLocked || false,
          pageCountLocked: metadata.pageCountLocked || false,
          languageLocked: metadata.languageLocked || false,
          amazonRatingLocked: metadata.amazonRatingLocked || false,
          amazonReviewCountLocked: metadata.amazonReviewCountLocked || false,
          goodreadsRatingLocked: metadata.goodreadsRatingLocked || false,
          goodreadsReviewCountLocked: metadata.goodreadsReviewCountLocked || false,
          seriesNameLocked: metadata.seriesNameLocked || false,
          seriesNumberLocked: metadata.seriesNumberLocked || false,
          seriesTotalLocked: metadata.seriesTotalLocked || false,
          thumbnailUrlLocked: metadata.coverLocked || false,
        });

        if (metadata.titleLocked) this.metadataForm.get('title')?.disable();
        if (metadata.subtitleLocked) this.metadataForm.get('subtitle')?.disable();
        if (metadata.authorsLocked) this.metadataForm.get('authors')?.disable();
        if (metadata.categoriesLocked) this.metadataForm.get('categories')?.disable();
        if (metadata.publisherLocked) this.metadataForm.get('publisher')?.disable();
        if (metadata.publishedDateLocked) this.metadataForm.get('publishedDate')?.disable();
        if (metadata.languageLocked) this.metadataForm.get('language')?.disable();
        if (metadata.isbn10Locked) this.metadataForm.get('isbn10')?.disable();
        if (metadata.isbn13Locked) this.metadataForm.get('isbn13')?.disable();
        if (metadata.asinLocked) this.metadataForm.get('asin')?.disable();
        if (metadata.amazonReviewCountLocked) this.metadataForm.get('amazonReviewCount')?.disable();
        if (metadata.amazonRatingLocked) this.metadataForm.get('amazonRating')?.disable();
        if (metadata.goodreadsReviewCountLocked) this.metadataForm.get('goodreadsReviewCount')?.disable();
        if (metadata.goodreadsRatingLocked) this.metadataForm.get('goodreadsRating')?.disable();
        if (metadata.pageCountLocked) this.metadataForm.get('pageCount')?.disable();
        if (metadata.descriptionLocked) this.metadataForm.get('description')?.disable();
        if (metadata.seriesNameLocked) this.metadataForm.get('seriesName')?.disable();
        if (metadata.seriesNumberLocked) this.metadataForm.get('seriesNumber')?.disable();
        if (metadata.seriesTotalLocked) this.metadataForm.get('seriesTotal')?.disable();
      }
    });
  }

  onSave(): void {
    const updatedBookMetadata = this.buildMetadata(undefined);
    this.bookService.updateBookMetadata(this.currentBookId, updatedBookMetadata, false).subscribe({
      next: (bookMetadata) => {
        Object.keys(this.copiedFields).forEach((field) => {
          if (this.copiedFields[field]) {
            this.savedFields[field] = true;
          }
        });
        this.messageService.add({severity: 'info', summary: 'Success', detail: 'Book metadata updated'});
        this.metadataCenterService.emitMetadata(bookMetadata);
      },
      error: () => {
        this.messageService.add({severity: 'error', summary: 'Error', detail: 'Failed to update book metadata'});
      }
    });
  }

  private buildMetadata(shouldLockAllFields: boolean | undefined) {
    const updatedBookMetadata: BookMetadata = {
      bookId: this.currentBookId,
      title: this.metadataForm.get('title')?.value || this.copiedFields['title'] ? this.getValueOrCopied('title') : '',
      subtitle: this.metadataForm.get('subtitle')?.value || this.copiedFields['subtitle'] ? this.getValueOrCopied('subtitle') : '',
      authors: this.metadataForm.get('authors')?.value || this.copiedFields['authors'] ? this.getArrayFromFormField('authors', this.fetchedMetadata.authors) : [],
      categories: this.metadataForm.get('categories')?.value || this.copiedFields['categories'] ? this.getArrayFromFormField('categories', this.fetchedMetadata.categories) : [],
      publisher: this.metadataForm.get('publisher')?.value || this.copiedFields['publisher'] ? this.getValueOrCopied('publisher') : '',
      publishedDate: this.metadataForm.get('publishedDate')?.value || this.copiedFields['publishedDate'] ? this.getValueOrCopied('publishedDate') : '',
      isbn10: this.metadataForm.get('isbn10')?.value || this.copiedFields['isbn10'] ? this.getValueOrCopied('isbn10') : '',
      isbn13: this.metadataForm.get('isbn13')?.value || this.copiedFields['isbn13'] ? this.getValueOrCopied('isbn13') : '',
      asin: this.metadataForm.get('asin')?.value || this.copiedFields['asin'] ? this.getValueOrCopied('asin') : '',
      description: this.metadataForm.get('description')?.value || this.copiedFields['description'] ? this.getValueOrCopied('description') : '',
      pageCount: this.metadataForm.get('pageCount')?.value || this.copiedFields['pageCount'] ? this.getPageCountOrCopied() : null,
      language: this.metadataForm.get('language')?.value || this.copiedFields['language'] ? this.getValueOrCopied('language') : '',
      amazonRating: this.metadataForm.get('amazonRating')?.value || this.copiedFields['amazonRating'] ? this.getNumberOrCopied('amazonRating') : null,
      amazonReviewCount: this.metadataForm.get('amazonReviewCount')?.value || this.copiedFields['amazonReviewCount'] ? this.getNumberOrCopied('amazonReviewCount') : null,
      goodreadsRating: this.metadataForm.get('goodreadsRating')?.value || this.copiedFields['goodreadsRating'] ? this.getNumberOrCopied('goodreadsRating') : null,
      goodreadsReviewCount: this.metadataForm.get('goodreadsReviewCount')?.value || this.copiedFields['goodreadsReviewCount'] ? this.getNumberOrCopied('goodreadsReviewCount') : null,
      seriesName: this.metadataForm.get('seriesName')?.value || this.copiedFields['seriesName'] ? this.getValueOrCopied('seriesName') : '',
      seriesNumber: this.metadataForm.get('seriesNumber')?.value || this.copiedFields['seriesNumber'] ? this.getNumberOrCopied('seriesNumber') : null,
      seriesTotal: this.metadataForm.get('seriesTotal')?.value || this.copiedFields['seriesTotal'] ? this.getNumberOrCopied('seriesTotal') : null,
      thumbnailUrl: this.getThumbnail(),

      titleLocked: this.metadataForm.get('titleLocked')?.value,
      subtitleLocked: this.metadataForm.get('subtitleLocked')?.value,
      authorsLocked: this.metadataForm.get('authorsLocked')?.value,
      categoriesLocked: this.metadataForm.get('categoriesLocked')?.value,
      publisherLocked: this.metadataForm.get('publisherLocked')?.value,
      publishedDateLocked: this.metadataForm.get('publishedDateLocked')?.value,
      isbn10Locked: this.metadataForm.get('isbn10Locked')?.value,
      isbn13Locked: this.metadataForm.get('isbn13Locked')?.value,
      asinLocked: this.metadataForm.get('asin')?.value,
      descriptionLocked: this.metadataForm.get('descriptionLocked')?.value,
      pageCountLocked: this.metadataForm.get('pageCountLocked')?.value,
      languageLocked: this.metadataForm.get('languageLocked')?.value,
      amazonRatingLocked: this.metadataForm.get('amazonRatingLocked')?.value,
      amazonReviewCountLocked: this.metadataForm.get('amazonReviewCountLocked')?.value,
      goodreadsRatingLocked: this.metadataForm.get('goodreadsRatingLocked')?.value,
      goodreadsReviewCountLocked: this.metadataForm.get('goodreadsReviewCountLocked')?.value,
      seriesNameLocked: this.metadataForm.get('seriesNameLocked')?.value,
      seriesNumberLocked: this.metadataForm.get('seriesNumberLocked')?.value,
      seriesTotalLocked: this.metadataForm.get('seriesTotalLocked')?.value,
      coverLocked: this.metadataForm.get('thumbnailUrlLocked')?.value,

      ...(shouldLockAllFields !== undefined && {allFieldsLocked: shouldLockAllFields}),
    };
    return updatedBookMetadata;
  }

  getThumbnail() {
    const thumbnailUrl = this.metadataForm.get('thumbnailUrl')?.value;
    if (thumbnailUrl?.includes('api/v1')) {
      return null;
    }
    return this.copiedFields['thumbnailUrl'] ? this.getValueOrCopied('thumbnailUrl') : null;
  }

  private updateMetadata(shouldLockAllFields: boolean | undefined): void {
    this.bookService.updateBookMetadata(this.currentBookId, this.buildMetadata(shouldLockAllFields), false).subscribe({
      next: (response) => {
        this.metadataCenterService.emitMetadata(response);

        if (shouldLockAllFields !== undefined) {
          this.messageService.add({
            severity: 'success',
            summary: shouldLockAllFields ? 'Metadata Locked' : 'Metadata Unlocked',
            detail: shouldLockAllFields
              ? 'All fields have been successfully locked.'
              : 'All fields have been successfully unlocked.',
          });
        }
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update lock state',
        });
      }
    });
  }

  toggleLock(field: string): void {
    const isLocked = this.metadataForm.get(field + 'Locked')?.value;
    const updatedLockedState = !isLocked;
    this.metadataForm.get(field + 'Locked')?.setValue(updatedLockedState);
    if (updatedLockedState) {
      this.metadataForm.get(field)?.disable();
    } else {
      this.metadataForm.get(field)?.enable();
    }
    this.updateMetadata(undefined);
  }

  copyMissing(): void {
    Object.keys(this.fetchedMetadata).forEach((field) => {
      const isLocked = this.metadataForm.get(`${field}Locked`)?.value;
      if (!isLocked && !this.metadataForm.get(field)?.value && this.fetchedMetadata[field]) {
        this.copyFetchedToCurrent(field);
      }
    });
  }

  copyAll() {
    Object.keys(this.fetchedMetadata).forEach((field) => {
      const isLocked = this.metadataForm.get(`${field}Locked`)?.value;
      if (!isLocked && this.fetchedMetadata[field] && field !== 'thumbnailUrl') {
        this.copyFetchedToCurrent(field);
      }
    });
  }

  copyFetchedToCurrent(field: string): void {
    const isLocked = this.metadataForm.get(`${field}Locked`)?.value;
    if (isLocked) {
      this.messageService.add({
        severity: 'warn',
        summary: 'Action Blocked',
        detail: `${field} is locked and cannot be updated.`
      });
      return;
    }
    const value = this.fetchedMetadata[field];
    if (value) {
      this.metadataForm.get(field)?.setValue(value);
      this.copiedFields[field] = true;
      this.highlightCopiedInput(field);
    }
  }

  private getNumberOrCopied(field: string): number | null {
    const formValue = this.metadataForm.get(field)?.value;
    if (formValue === '' || formValue === null || isNaN(formValue)) {
      this.copiedFields[field] = true;
      return this.fetchedMetadata[field] || null;
    }
    return Number(formValue);
  }

  private getPageCountOrCopied(): number | null {
    const formValue = this.metadataForm.get('pageCount')?.value;
    if (formValue === '' || formValue === null || isNaN(formValue)) {
      this.copiedFields['pageCount'] = true;
      return this.fetchedMetadata.pageCount || null;
    }
    return Number(formValue);
  }

  private getValueOrCopied(field: string): string {
    const formValue = this.metadataForm.get(field)?.value;
    if (!formValue || formValue === '') {
      this.copiedFields[field] = true;
      return this.fetchedMetadata[field] || '';
    }
    return formValue;
  }

  getArrayFromFormField(field: string, fallbackValue: any): any[] {
    const fieldValue = this.metadataForm.get(field)?.value;
    if (!fieldValue) {
      return fallbackValue ? (Array.isArray(fallbackValue) ? fallbackValue : [fallbackValue]) : [];
    }
    if (typeof fieldValue === 'string') {
      return fieldValue.split(',').map(item => item.trim());
    }
    return Array.isArray(fieldValue) ? fieldValue : [];
  }

  lockAll(): void {
    Object.keys(this.metadataForm.controls).forEach((key) => {
      if (key.endsWith('Locked')) {
        this.metadataForm.get(key)?.setValue(true);
        const fieldName = key.replace('Locked', '');
        this.metadataForm.get(fieldName)?.disable();
      }
    });
    this.updateMetadata(true);
  }

  unlockAll(): void {
    Object.keys(this.metadataForm.controls).forEach((key) => {
      if (key.endsWith('Locked')) {
        this.metadataForm.get(key)?.setValue(false);
        const fieldName = key.replace('Locked', '');
        this.metadataForm.get(fieldName)?.enable();
      }
    });
    this.updateMetadata(false);
  }

  highlightCopiedInput(field: string): void {
    this.copiedFields = {...this.copiedFields, [field]: true};
  }

  isValueCopied(field: string): boolean {
    return this.copiedFields[field];
  }

  isValueSaved(field: string): boolean {
    return this.savedFields[field];
  }

  goBackClick() {
    this.goBack.emit(true);
  }

  hoveredFields: { [key: string]: boolean } = {};

  onMouseEnter(controlName: string): void {
    if (this.isValueCopied(controlName) && !this.isValueSaved(controlName)) {
      this.hoveredFields[controlName] = true;
    }
  }

  onMouseLeave(controlName: string): void {
    this.hoveredFields[controlName] = false;
  }

  resetField(field: string) {
    this.metadataForm.get(field)?.setValue(this.originalMetadata[field]);
    this.copiedFields[field] = false;
    this.hoveredFields[field] = false;
  }
}
