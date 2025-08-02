import {Component, DestroyRef, inject, OnInit, QueryList, ViewChildren} from '@angular/core';
import {takeUntilDestroyed} from '@angular/core/rxjs-interop';
import {filter, take} from 'rxjs/operators';

import {BookdropFile, BookdropFileTaskService, BookdropFinalizePayload, BookdropFinalizeResult} from '../bookdrop-file-task.service';
import {LibraryService} from '../../book/service/library.service';
import {Library} from '../../book/model/library.model';

import {ProgressSpinner} from 'primeng/progressspinner';
import {DropdownModule} from 'primeng/dropdown';
import {FormControl, FormGroup, FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {Select} from 'primeng/select';
import {InputText} from 'primeng/inputtext';
import {Tooltip} from 'primeng/tooltip';
import {Divider} from 'primeng/divider';
import {ConfirmationService, MessageService} from 'primeng/api';

import {BookdropFileMetadataPickerComponent} from '../bookdrop-file-metadata-picker-component/bookdrop-file-metadata-picker.component';
import {Observable} from 'rxjs';

import {AppSettings} from '../../core/model/app-settings.model';
import {AppSettingsService} from '../../core/service/app-settings.service';
import {BookdropFinalizeResultDialogComponent} from '../bookdrop-finalize-result-dialog-component/bookdrop-finalize-result-dialog-component';
import {DialogService} from 'primeng/dynamicdialog';
import {BookMetadata} from '../../book/model/book.model';
import {UrlHelperService} from '../../utilities/service/url-helper.service';

export interface BookdropFileUI {
  file: BookdropFile;
  metadataForm: FormGroup;
  copiedFields: Record<string, boolean>;
  savedFields: Record<string, boolean>;
  selected: boolean;
  showDetails: boolean;
  selectedLibraryId: string | null;
  selectedPathId: string | null;
  availablePaths: { id: string; name: string }[];
}

@Component({
  selector: 'app-bookdrop-file-review-component',
  standalone: true,
  templateUrl: './bookdrop-file-review.component.html',
  styleUrl: './bookdrop-file-review.component.scss',
  imports: [
    ProgressSpinner,
    DropdownModule,
    FormsModule,
    Button,
    Select,
    BookdropFileMetadataPickerComponent,
    Tooltip,
    Divider,
    InputText,

  ],
})
export class BookdropFileReviewComponent implements OnInit {
  private readonly bookdropFileService = inject(BookdropFileTaskService);
  private readonly libraryService = inject(LibraryService);
  private readonly confirmationService = inject(ConfirmationService);
  private readonly destroyRef = inject(DestroyRef);
  private readonly dialogService = inject(DialogService);
  private readonly appSettingsService = inject(AppSettingsService);
  private readonly messageService = inject(MessageService);

  @ViewChildren('metadataPicker') metadataPickers!: QueryList<BookdropFileMetadataPickerComponent>;

  uploadPattern = '';
  defaultLibraryId: string | null = null;
  defaultPathId: string | null = null;
  bookdropFileUis: BookdropFileUI[] = [];
  libraries: Library[] = [];
  copiedFlags: Record<number, boolean> = {};
  loading = true;
  saving = false;
  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  protected urlHelper = inject(UrlHelperService);

  ngOnInit(): void {
    this.bookdropFileService.getPendingFiles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe(files => {
        this.bookdropFileUis = files.map(file => this.createFileUI(file));
        this.loading = false;
      });

    this.libraryService.libraryState$
      .pipe(filter(state => !!state?.loaded), take(1))
      .subscribe(state => {
        this.libraries = state.libraries ?? [];
      });

    this.appSettings$
      .pipe(filter(Boolean), take(1))
      .subscribe(settings => {
        this.uploadPattern = settings?.uploadPattern ?? '';
      });
  }

  get libraryOptions() {
    return this.libraries.map(lib => ({label: lib.name, value: String(lib.id ?? '')}));
  }

  get selectedLibraryPaths() {
    const selectedLibrary = this.libraries.find(lib => String(lib.id) === this.defaultLibraryId);
    return selectedLibrary?.paths.map(path => ({label: path.path, value: String(path.id ?? '')})) ?? [];
  }

  onLibraryChange(file: BookdropFileUI): void {
    const lib = this.libraries.find(l => String(l.id) === file.selectedLibraryId);
    file.availablePaths = lib?.paths.map(p => ({id: String(p.id ?? ''), name: p.path})) ?? [];
    file.selectedPathId = null;
  }

  onMetadataCopied(fileId: number, copied: boolean): void {
    this.copiedFlags[fileId] = copied;
  }

  applyDefaultsToAll(): void {
    if (!this.defaultLibraryId) return;

    const selectedLib = this.libraries.find(l => String(l.id) === this.defaultLibraryId);
    const selectedPaths = selectedLib?.paths ?? [];

    for (const file of this.bookdropFileUis) {
      file.selectedLibraryId = this.defaultLibraryId;
      file.availablePaths = selectedPaths.map(path => ({id: String(path.id), name: path.path}));
      file.selectedPathId = this.defaultPathId ?? null;
    }
  }

  get canApplyDefaults(): boolean {
    return !!(this.defaultLibraryId && this.defaultPathId);
  }

  copyAll(includeThumbnail: boolean): void {
    for (const fileUi of this.bookdropFileUis) {
      const fetched = fileUi.file.fetchedMetadata;
      const form = fileUi.metadataForm;
      if (!fetched) continue;
      for (const key of Object.keys(fetched)) {
        if (!includeThumbnail && key === 'thumbnailUrl') continue;
        const value = fetched[key as keyof typeof fetched];
        if (value != null) {
          form.get(key)?.setValue(value);
          fileUi.copiedFields[key] = true;
        }
      }
      this.onMetadataCopied(fileUi.file.id, true);
    }
  }

  resetAll(): void {
    for (const fileUi of this.bookdropFileUis) {
      const original = fileUi.file.originalMetadata;
      fileUi.metadataForm.patchValue({
        title: original.title || null,
        subtitle: original.subtitle || null,
        authors: [...(original.authors ?? [])].sort(),
        categories: [...(original.categories ?? [])].sort(),
        publisher: original.publisher || null,
        publishedDate: original.publishedDate || null,
        isbn10: original.isbn10 || null,
        isbn13: original.isbn13 || null,
        description: original.description || null,
        pageCount: original.pageCount || null,
        language: original.language || null,
        asin: original.asin || null,
        amazonRating: original.amazonRating || null,
        amazonReviewCount: original.amazonReviewCount || null,
        goodreadsId: original.goodreadsId || null,
        goodreadsRating: original.goodreadsRating || null,
        goodreadsReviewCount: original.goodreadsReviewCount || null,
        hardcoverId: original.hardcoverId || null,
        hardcoverRating: original.hardcoverRating || null,
        hardcoverReviewCount: original.hardcoverReviewCount || null,
        googleId: original.googleId || null,
        comicvineId: original.comicvineId || null,
        seriesName: original.seriesName || null,
        seriesNumber: original.seriesNumber || null,
        seriesTotal: original.seriesTotal || null,
        thumbnailUrl: this.urlHelper.getBookdropCoverUrl(fileUi.file.id),
      });
      fileUi.copiedFields = {};
      fileUi.savedFields = {};
    }
    this.copiedFlags = {};
  }

  get canFinalize(): boolean {
    return this.bookdropFileUis.length > 0 &&
      this.bookdropFileUis.every(file => file.selectedLibraryId && file.selectedPathId);
  }

  confirmFinalize(): void {
    this.confirmationService.confirm({
      message: 'Are you sure you want to finalize the import?',
      header: 'Confirm Finalize',
      icon: 'pi pi-exclamation-triangle',
      acceptLabel: 'Yes',
      rejectLabel: 'Cancel',
      accept: () => this.finalizeImport(),
    });
  }

  private finalizeImport(): void {
    this.saving = true;
    const payload = this.buildFinalizePayload();

    this.bookdropFileService.finalizeImport(payload).subscribe({
      next: (result: BookdropFinalizeResult) => {
        this.saving = false;

        this.messageService.add({
          severity: 'success',
          summary: 'Import Complete',
          detail: 'Import process finished. See details below.',
        });

        this.dialogService.open(BookdropFinalizeResultDialogComponent, {
          header: 'Import Summary',
          modal: true,
          closable: true,
          closeOnEscape: true,
          data: {
            results: result.results
          }
        });

        this.reloadPendingFiles();
      },
      error: (err) => {
        console.error('Error finalizing import:', err);
        this.messageService.add({
          severity: 'error',
          summary: 'Import Failed',
          detail: 'Some files could not be moved. Please check the console for more details.',
        });
        this.saving = false;
      }
    });
  }

  private buildFinalizePayload(): BookdropFinalizePayload {
    return {
      uploadPattern: this.uploadPattern,
      files: this.bookdropFileUis.map((fileUi, index) => {
        const rawMetadata = this.bookdropFileUis[index].metadataForm.value;
        const metadata = {...rawMetadata};
        if (metadata.thumbnailUrl?.includes('/api/bookdrop/')) {
          delete metadata.thumbnailUrl;
        }
        return {
          fileId: fileUi.file.id,
          libraryId: Number(fileUi.selectedLibraryId),
          pathId: Number(fileUi.selectedPathId),
          metadata,
        };
      }),
    };
  }

  private reloadPendingFiles(): void {
    this.loading = true;
    this.bookdropFileService.getPendingFiles()
      .pipe(takeUntilDestroyed(this.destroyRef))
      .subscribe({
        next: files => {
          this.bookdropFileUis = files.map(file => this.createFileUI(file));
          this.loading = false;
          this.saving = false;
        },
        error: err => {
          console.error('Error loading pending files:', err);
          this.loading = false;
          this.saving = false;
        }
      });
  }

  confirmDelete(): void {
    this.confirmationService.confirm({
      message: 'Are you sure you want to delete all Bookdrop files? This action cannot be undone.',
      header: 'Confirm Delete',
      icon: 'pi pi-exclamation-triangle',
      accept: () => {
        this.bookdropFileService.discardAllFile().subscribe({
          next: () => {
            this.messageService.add({
              severity: 'success',
              summary: 'Files Deleted',
              detail: 'All Bookdrop files were deleted successfully.',
            });
            this.reloadPendingFiles();
          },
          error: (err) => {
            this.messageService.add({
              severity: 'error',
              summary: 'Delete Failed',
              detail: 'An error occurred while deleting Bookdrop files.',
            });
          },
        });
      },
    });
  }

  private createMetadataForm(original: BookMetadata, bookdropFileId: number): FormGroup {
    return new FormGroup({
      title: new FormControl(original.title ?? ''),
      subtitle: new FormControl(original.subtitle ?? ''),
      authors: new FormControl([...(original.authors ?? [])].sort()),
      categories: new FormControl([...(original.categories ?? [])].sort()),
      publisher: new FormControl(original.publisher ?? ''),
      publishedDate: new FormControl(original.publishedDate ?? ''),
      isbn10: new FormControl(original.isbn10 ?? ''),
      isbn13: new FormControl(original.isbn13 ?? ''),
      description: new FormControl(original.description ?? ''),
      pageCount: new FormControl(original.pageCount ?? ''),
      language: new FormControl(original.language ?? ''),
      asin: new FormControl(original.asin ?? ''),
      amazonRating: new FormControl(original.amazonRating ?? ''),
      amazonReviewCount: new FormControl(original.amazonReviewCount ?? ''),
      goodreadsId: new FormControl(original.goodreadsId ?? ''),
      goodreadsRating: new FormControl(original.goodreadsRating ?? ''),
      goodreadsReviewCount: new FormControl(original.goodreadsReviewCount ?? ''),
      hardcoverId: new FormControl(original.hardcoverId ?? ''),
      hardcoverRating: new FormControl(original.hardcoverRating ?? ''),
      hardcoverReviewCount: new FormControl(original.hardcoverReviewCount ?? ''),
      googleId: new FormControl(original.googleId ?? ''),
      comicvineId: new FormControl(original.comicvineId ?? ''),
      seriesName: new FormControl(original.seriesName ?? ''),
      seriesNumber: new FormControl(original.seriesNumber ?? ''),
      seriesTotal: new FormControl(original.seriesTotal ?? ''),
      thumbnailUrl: new FormControl(this.urlHelper.getBookdropCoverUrl(bookdropFileId)),
    });
  }

  private createFileUI(file: BookdropFile): BookdropFileUI {
    const metadataForm = this.createMetadataForm(file.originalMetadata, file.id);
    return {
      file,
      selected: false,
      showDetails: false,
      selectedLibraryId: null,
      selectedPathId: null,
      availablePaths: [],
      metadataForm,
      copiedFields: {},
      savedFields: {}
    };
  }
}
