import {Component, DestroyRef, EventEmitter, inject, Input, OnInit, Output, ViewChild} from '@angular/core';
import {InputText} from 'primeng/inputtext';
import {Button} from 'primeng/button';
import {Divider} from 'primeng/divider';
import {FormControl, FormGroup, FormsModule, ReactiveFormsModule} from '@angular/forms';
import {Observable} from 'rxjs';
import {AsyncPipe, NgClass} from '@angular/common';
import {MessageService} from 'primeng/api';
import {Book, BookMetadata} from '../../../model/book.model';
import {UrlHelperService} from '../../../../utilities/service/url-helper.service';
import {FileUpload, FileUploadErrorEvent, FileUploadEvent} from 'primeng/fileupload';
import {HttpResponse} from '@angular/common/http';
import {BookService} from '../../../service/book.service';
import {ProgressSpinner} from 'primeng/progressspinner';
import {Tooltip} from 'primeng/tooltip';
import {Editor} from 'primeng/editor';
import {debounceTime} from 'rxjs/operators';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {MetadataRestoreDialogComponent} from '../../../components/book-browser/metadata-restore-dialog-component/metadata-restore-dialog-component';
import {DialogService} from 'primeng/dynamicdialog';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {MetadataRefreshRequest} from '../../model/request/metadata-refresh-request.model';
import {MetadataRefreshType} from '../../model/request/metadata-refresh-type.enum';

@Component({
  selector: 'app-metadata-editor',
  standalone: true,
  templateUrl: './metadata-editor.component.html',
  styleUrls: ['./metadata-editor.component.scss'],
  imports: [
    InputText,
    Button,
    Divider,
    FormsModule,
    AsyncPipe,
    ReactiveFormsModule,
    FileUpload,
    ProgressSpinner,
    NgClass,
    Tooltip,
    Editor,
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel
  ]
})
export class MetadataEditorComponent implements OnInit {

  @ViewChild(Editor) quillEditor!: Editor;

  @Input() book$!: Observable<Book | null>;
  @Output() nextBookClicked = new EventEmitter<void>();
  @Output() previousBookClicked = new EventEmitter<void>();
  @Output() closeDialogButtonClicked = new EventEmitter<void>();

  @Input() disableNext = false;
  @Input() disablePrevious = false;
  @Input() showNavigationButtons = false;

  private messageService = inject(MessageService);
  private bookService = inject(BookService);
  protected urlHelper = inject(UrlHelperService);
  private dialogService = inject(DialogService);
  private destroyRef = inject(DestroyRef);

  metadataForm: FormGroup;
  currentBookId!: number;
  isUploading = false;
  isLoading = false;
  htmlTextarea = '';
  isSaving = false;

  refreshingBookIds = new Set<number>();
  isAutoFetching = false;

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
      description: new FormControl(''),
      pageCount: new FormControl(''),
      language: new FormControl(''),
      rating: new FormControl(''),
      reviewCount: new FormControl(''),
      asin: new FormControl(''),
      amazonRating: new FormControl(''),
      amazonReviewCount: new FormControl(''),
      goodreadsId: new FormControl(''),
      goodreadsRating: new FormControl(''),
      goodreadsReviewCount: new FormControl(''),
      hardcoverId: new FormControl(''),
      hardcoverRating: new FormControl(''),
      hardcoverReviewCount: new FormControl(''),
      googleId: new FormControl(''),
      seriesName: new FormControl(''),
      seriesNumber: new FormControl(''),
      seriesTotal: new FormControl(''),

      titleLocked: new FormControl(false),
      subtitleLocked: new FormControl(false),
      authorsLocked: new FormControl(false),
      categoriesLocked: new FormControl(false),
      publisherLocked: new FormControl(false),
      publishedDateLocked: new FormControl(false),
      isbn10Locked: new FormControl(false),
      isbn13Locked: new FormControl(false),
      descriptionLocked: new FormControl(false),
      pageCountLocked: new FormControl(false),
      languageLocked: new FormControl(false),
      asinLocked: new FormControl(false),
      amazonRatingLocked: new FormControl(false),
      amazonReviewCountLocked: new FormControl(false),
      goodreadsIdLocked: new FormControl(''),
      goodreadsRatingLocked: new FormControl(false),
      goodreadsReviewCountLocked: new FormControl(false),
      hardcoverIdLocked: new FormControl(false),
      hardcoverRatingLocked: new FormControl(false),
      hardcoverReviewCountLocked: new FormControl(false),
      googleIdLocked: new FormControl(false),
      seriesNameLocked: new FormControl(false),
      seriesNumberLocked: new FormControl(false),
      seriesTotalLocked: new FormControl(false),
      thumbnailUrlLocked: new FormControl(false),
    });
  }

  ngOnInit(): void {
    this.book$
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(book => {
        const metadata = book?.metadata;
        if (!metadata) return;

        this.currentBookId = metadata.bookId;

        if (this.refreshingBookIds.has(book.id)) {
          this.refreshingBookIds.delete(book.id);
          this.isAutoFetching = false;
        }

        if (this.quillEditor?.quill) {
          this.quillEditor.quill.root.innerHTML = metadata.description;
          this.quillDisabled();
        }

        this.populateFormFromMetadata(metadata);
      });

    this.metadataForm.get('description')?.valueChanges
      .pipe(debounceTime(100), takeUntilDestroyed(this.destroyRef))
      .subscribe(value => {
        if (this.htmlTextarea !== value) {
          this.htmlTextarea = value;
        }
      });
  }

  private populateFormFromMetadata(metadata: BookMetadata): void {
    this.metadataForm.patchValue({
      title: metadata.title ?? null,
      subtitle: metadata.subtitle ?? null,
      authors: (metadata.authors ?? []).sort().join(', '),
      categories: (metadata.categories ?? []).sort().join(', '),
      publisher: metadata.publisher ?? null,
      publishedDate: metadata.publishedDate ?? null,
      isbn10: metadata.isbn10 ?? null,
      isbn13: metadata.isbn13 ?? null,
      description: metadata.description ?? null,
      pageCount: metadata.pageCount ?? null,
      language: metadata.language ?? null,
      rating: metadata.rating ?? null,
      reviewCount: metadata.reviewCount ?? null,
      asin: metadata.asin ?? null,
      amazonRating: metadata.amazonRating ?? null,
      amazonReviewCount: metadata.amazonReviewCount ?? null,
      goodreadsId: metadata.goodreadsId ?? null,
      goodreadsRating: metadata.goodreadsRating ?? null,
      goodreadsReviewCount: metadata.goodreadsReviewCount ?? null,
      hardcoverId: metadata.hardcoverId ?? null,
      hardcoverRating: metadata.hardcoverRating ?? null,
      hardcoverReviewCount: metadata.hardcoverReviewCount ?? null,
      googleId: metadata.googleId ?? null,
      seriesName: metadata.seriesName ?? null,
      seriesNumber: metadata.seriesNumber ?? null,
      seriesTotal: metadata.seriesTotal ?? null,
      titleLocked: metadata.titleLocked ?? false,
      subtitleLocked: metadata.subtitleLocked ?? false,
      authorsLocked: metadata.authorsLocked ?? false,
      categoriesLocked: metadata.categoriesLocked ?? false,
      publisherLocked: metadata.publisherLocked ?? false,
      publishedDateLocked: metadata.publishedDateLocked ?? false,
      isbn10Locked: metadata.isbn10Locked ?? false,
      isbn13Locked: metadata.isbn13Locked ?? false,
      descriptionLocked: metadata.descriptionLocked ?? false,
      pageCountLocked: metadata.pageCountLocked ?? false,
      languageLocked: metadata.languageLocked ?? false,
      asinLocked: metadata.asinLocked ?? false,
      amazonRatingLocked: metadata.amazonRatingLocked ?? false,
      amazonReviewCountLocked: metadata.amazonReviewCountLocked ?? false,
      goodreadsIdLocked: metadata.goodreadsIdLocked ?? false,
      goodreadsRatingLocked: metadata.goodreadsRatingLocked ?? false,
      goodreadsReviewCountLocked: metadata.goodreadsReviewCountLocked ?? false,
      hardcoverIdLocked: metadata.hardcoverIdLocked ?? false,
      hardcoverRatingLocked: metadata.hardcoverRatingLocked ?? false,
      hardcoverReviewCountLocked: metadata.hardcoverReviewCountLocked ?? false,
      googleIdLocked: metadata.googleIdLocked ?? false,
      seriesNameLocked: metadata.seriesNameLocked ?? false,
      seriesNumberLocked: metadata.seriesNumberLocked ?? false,
      seriesTotalLocked: metadata.seriesTotalLocked ?? false,
      thumbnailUrlLocked: metadata.coverLocked ?? false,
    });

    const lockableFields: { key: keyof BookMetadata; control: string }[] = [
      {key: 'titleLocked', control: 'title'},
      {key: 'subtitleLocked', control: 'subtitle'},
      {key: 'authorsLocked', control: 'authors'},
      {key: 'categoriesLocked', control: 'categories'},
      {key: 'publisherLocked', control: 'publisher'},
      {key: 'publishedDateLocked', control: 'publishedDate'},
      {key: 'languageLocked', control: 'language'},
      {key: 'isbn10Locked', control: 'isbn10'},
      {key: 'isbn13Locked', control: 'isbn13'},
      {key: 'asinLocked', control: 'asin'},
      {key: 'amazonReviewCountLocked', control: 'amazonReviewCount'},
      {key: 'amazonRatingLocked', control: 'amazonRating'},
      {key: 'goodreadsIdLocked', control: 'goodreadsId'},
      {key: 'goodreadsReviewCountLocked', control: 'goodreadsReviewCount'},
      {key: 'goodreadsRatingLocked', control: 'goodreadsRating'},
      {key: 'hardcoverIdLocked', control: 'hardcoverId'},
      {key: 'hardcoverReviewCountLocked', control: 'hardcoverReviewCount'},
      {key: 'hardcoverRatingLocked', control: 'hardcoverRating'},
      {key: 'googleIdLocked', control: 'googleId'},
      {key: 'pageCountLocked', control: 'pageCount'},
      {key: 'descriptionLocked', control: 'description'},
      {key: 'seriesNameLocked', control: 'seriesName'},
      {key: 'seriesNumberLocked', control: 'seriesNumber'},
      {key: 'seriesTotalLocked', control: 'seriesTotal'}
    ];

    for (const {key, control} of lockableFields) {
      const isLocked = metadata[key] === true;
      const formControl = this.metadataForm.get(control);
      if (formControl) {
        isLocked ? formControl.disable() : formControl.enable();
      }
    }

    this.metadataForm.get('reviewCount')?.disable();
    this.metadataForm.get('rating')?.disable();
  }

  onSave(): void {
    this.isSaving = true;
    this.bookService.updateBookMetadata(this.currentBookId, this.buildMetadata(undefined), false).subscribe({
      next: (response) => {
        this.isSaving = false;
        this.messageService.add({severity: 'info', summary: 'Success', detail: 'Book metadata updated'});
      },
      error: (err) => {
        this.isSaving = false;
        console.error('Update metadata failed:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: err?.error?.message || 'Failed to update book metadata'
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

  quillDisabled(): boolean {
    return this.metadataForm.get('descriptionLocked')?.value === true;
  }

  onHtmlTextareaChange(value: string): void {
    this.htmlTextarea = value;
    const control = this.metadataForm.get('description');
    if (control?.value !== value) {
      control?.patchValue(value, {emitEvent: false});
      if (this.quillEditor && this.quillEditor.quill) {
        this.quillEditor.quill.root.innerHTML = value;
      }
    }
  }

  private buildMetadata(shouldLockAllFields: boolean | undefined) {
    const updatedBookMetadata: BookMetadata = {
      bookId: this.currentBookId,
      title: this.metadataForm.get('title')?.value,
      subtitle: this.metadataForm.get('subtitle')?.value,
      authors: this.metadataForm.get('authors')?.value.split(',').map((author: string) => author.trim()),
      categories: this.metadataForm.get('categories')?.value.split(',').map((category: string) => category.trim()),
      publisher: this.metadataForm.get('publisher')?.value,
      publishedDate: this.metadataForm.get('publishedDate')?.value,
      isbn10: this.metadataForm.get('isbn10')?.value,
      isbn13: this.metadataForm.get('isbn13')?.value,
      description: this.metadataForm.get('description')?.value,
      pageCount: this.metadataForm.get('pageCount')?.value,
      rating: this.metadataForm.get('rating')?.value,
      reviewCount: this.metadataForm.get('reviewCount')?.value,
      asin: this.metadataForm.get('asin')?.value,
      amazonRating: this.metadataForm.get('amazonRating')?.value,
      amazonReviewCount: this.metadataForm.get('amazonReviewCount')?.value,
      goodreadsId: this.metadataForm.get('goodreadsId')?.value,
      goodreadsRating: this.metadataForm.get('goodreadsRating')?.value,
      goodreadsReviewCount: this.metadataForm.get('goodreadsReviewCount')?.value,
      hardcoverId: this.metadataForm.get('hardcoverId')?.value,
      hardcoverRating: this.metadataForm.get('hardcoverRating')?.value,
      hardcoverReviewCount: this.metadataForm.get('hardcoverReviewCount')?.value,
      googleId: this.metadataForm.get('googleId')?.value,
      language: this.metadataForm.get('language')?.value,
      seriesName: this.metadataForm.get('seriesName')?.value,
      seriesNumber: this.metadataForm.get('seriesNumber')?.value,
      seriesTotal: this.metadataForm.get('seriesTotal')?.value,

      titleLocked: this.metadataForm.get('titleLocked')?.value,
      subtitleLocked: this.metadataForm.get('subtitleLocked')?.value,
      authorsLocked: this.metadataForm.get('authorsLocked')?.value,
      categoriesLocked: this.metadataForm.get('categoriesLocked')?.value,
      publisherLocked: this.metadataForm.get('publisherLocked')?.value,
      publishedDateLocked: this.metadataForm.get('publishedDateLocked')?.value,
      isbn10Locked: this.metadataForm.get('isbn10Locked')?.value,
      isbn13Locked: this.metadataForm.get('isbn13Locked')?.value,
      descriptionLocked: this.metadataForm.get('descriptionLocked')?.value,
      pageCountLocked: this.metadataForm.get('pageCountLocked')?.value,
      languageLocked: this.metadataForm.get('languageLocked')?.value,
      asinLocked: this.metadataForm.get('asinLocked')?.value,
      amazonRatingLocked: this.metadataForm.get('amazonRatingLocked')?.value,
      amazonReviewCountLocked: this.metadataForm.get('amazonReviewCountLocked')?.value,
      goodreadsIdLocked: this.metadataForm.get('goodreadsIdLocked')?.value,
      goodreadsRatingLocked: this.metadataForm.get('goodreadsRatingLocked')?.value,
      goodreadsReviewCountLocked: this.metadataForm.get('goodreadsReviewCountLocked')?.value,
      hardcoverIdLocked: this.metadataForm.get('hardcoverIdLocked')?.value,
      hardcoverRatingLocked: this.metadataForm.get('hardcoverRatingLocked')?.value,
      hardcoverReviewCountLocked: this.metadataForm.get('hardcoverReviewCountLocked')?.value,
      googleIdLocked: this.metadataForm.get('googleIdLocked')?.value,
      seriesNameLocked: this.metadataForm.get('seriesNameLocked')?.value,
      seriesNumberLocked: this.metadataForm.get('seriesNumberLocked')?.value,
      seriesTotalLocked: this.metadataForm.get('seriesTotalLocked')?.value,
      coverLocked: this.metadataForm.get('thumbnailUrlLocked')?.value,

      ...(shouldLockAllFields !== undefined && {allFieldsLocked: shouldLockAllFields}),
    };
    return updatedBookMetadata;
  }

  private updateMetadata(shouldLockAllFields: boolean | undefined): void {
    this.bookService.updateBookMetadata(this.currentBookId, this.buildMetadata(shouldLockAllFields), false).subscribe({
      next: (response) => {
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

  getUploadCoverUrl(): string {
    return this.bookService.getUploadCoverUrl(this.currentBookId);
  }

  onBeforeSend(): void {
    this.isUploading = true;
  }

  onUpload(event: FileUploadEvent): void {
    const response: HttpResponse<any> = event.originalEvent as HttpResponse<any>;
    if (response && response.status === 200) {
      const bookMetadata: BookMetadata = response.body as BookMetadata;
      this.bookService.handleBookMetadataUpdate(this.currentBookId, bookMetadata);
      this.isUploading = false;
    } else {
      this.isUploading = false;
      this.messageService.add({
        severity: 'error', summary: 'Upload Failed', detail: 'An error occurred while uploading the cover', life: 3000
      });
    }
  }

  onUploadError($event: FileUploadErrorEvent) {
    this.isUploading = false;
    this.messageService.add({
      severity: 'error', summary: 'Upload Error', detail: 'An error occurred while uploading the cover', life: 3000
    });
  }

  regenerateCover(bookId: number) {
    this.bookService.regenerateCover(bookId).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Book cover regenerated successfully. Refresh page to see the new cover.'
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to start cover regeneration'
        });
      }
    });
  }

  restoreMetadata() {
    this.dialogService.open(MetadataRestoreDialogComponent, {
      header: 'Restore Metadata from Backup',
      modal: true,
      closable: true,
      data: {
        bookId: [this.currentBookId]
      }
    });
  }

  autoFetch(bookId: number) {
    this.refreshingBookIds.add(bookId);
    this.isAutoFetching = true;
    const request: MetadataRefreshRequest = {
      quick: true,
      refreshType: MetadataRefreshType.BOOKS,
      bookIds: [bookId],
    };
    this.bookService.autoRefreshMetadata(request).subscribe();
    setTimeout(() => {
      this.isAutoFetching = false;
      this.refreshingBookIds.delete(bookId);
    }, 15000);
  }

  onNext() {
    this.nextBookClicked.emit();
  }

  onPrevious() {
    this.previousBookClicked.emit();
  }

  closeDialog() {
    this.closeDialogButtonClicked.emit();
  }
}
