import {Component, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {InputText} from 'primeng/inputtext';
import {Divider} from 'primeng/divider';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';
import {MessageService} from 'primeng/api';
import {filter, take} from 'rxjs/operators';

import {BookService} from '../book/service/book.service';
import {Book} from '../book/model/book.model';
import {FileMoveRequest, FileOperationsService} from '../file-operations-service';
import {LibraryService} from "../book/service/library.service";
import {AppSettingsService} from '../core/service/app-settings.service';
import {AppSettingKey} from '../core/model/app-settings.model';

@Component({
  selector: 'app-file-mover-component',
  standalone: true,
  imports: [Button, FormsModule, TableModule, InputText, Divider],
  templateUrl: './file-mover-component.html',
  styleUrl: './file-mover-component.scss'
})
export class FileMoverComponent {
  private config = inject(DynamicDialogConfig);
  private ref = inject(DynamicDialogRef);
  private bookService = inject(BookService);
  private libraryService = inject(LibraryService);
  private fileOperationsService = inject(FileOperationsService);
  private messageService = inject(MessageService);
  private appSettingsService = inject(AppSettingsService);

  movePattern = '';
  placeholdersVisible = false;
  loading = false;

  bookIds: Set<number> = new Set();
  books: Book[] = [];
  filePreviews: { originalPath: string; newPath: string; isMoved?: boolean }[] = [];

  constructor() {
    this.bookIds = new Set(this.config.data?.bookIds ?? []);
    this.books = this.bookService.getBooksByIdsFromState([...this.bookIds]);

    this.appSettingsService.appSettings$.pipe(
      filter(settings => settings != null),
      take(1)
    ).subscribe(settings => {
      this.movePattern = settings?.movePattern;
      this.applyPattern();
    });
  }

  applyPattern(): void {
    this.filePreviews = this.books.map(book => {
      const meta = book.metadata!;
      const fileName = book.fileName ?? '';
      const extension = fileName.match(/\.[^.]+$/)?.[0] ?? '';
      const fileSubPath = book.fileSubPath ? `${book.fileSubPath.replace(/\/+$/g, '')}/` : '';

      const relativeOriginalPath = `${fileSubPath}${fileName}`;
      const libraryPathPrefix =
        book.libraryPath?.id != null
          ? this.libraryService.getLibraryPathById(book.libraryPath.id)?.replace(/\/+$/g, '') ?? ''
          : '';
      const originalPath = `${libraryPathPrefix}/${relativeOriginalPath}`.replace(/\/\/+/g, '/');

      const values: Record<string, string> = {
        authors: this.sanitize(meta.authors?.join(', ') || 'Unknown Author'),
        title: this.sanitize(meta.title || 'Untitled'),
        year: this.formatYear(meta.publishedDate),
        series: this.sanitize(meta.seriesName || ''),
        seriesIndex: this.formatSeriesIndex(meta.seriesNumber ?? undefined),
        language: this.sanitize(meta.language || ''),
        publisher: this.sanitize(meta.publisher || ''),
        isbn: this.sanitize(meta.isbn13 || meta.isbn10 || ''),
        currentFilename: this.sanitize(fileName)
      };

      let newPath: string;

      if (!this.movePattern?.trim()) {
        // If pattern empty, just use current filename as the new path
        newPath = `${fileSubPath}${fileName}`;  // preserve subpath + filename
      } else {
        newPath = this.movePattern.replace(/<([^<>]+)>/g, (_, block) => {
          const placeholders = [...block.matchAll(/{(.*?)}/g)].map(m => m[1]);
          const allHaveValues = placeholders.every(key => values[key]?.trim());
          return allHaveValues
            ? block.replace(/{(.*?)}/g, (_: string, key: string) => values[key] ?? '')
            : '';
        });

        newPath = newPath.replace(/{(.*?)}/g, (_, key) => values[key] ?? '');

        // Append extension if not already present (most patterns won't include extension)
        if (!newPath.endsWith(extension)) {
          newPath += extension;
        }
      }

      const relativeNewPath = newPath;
      const fullNewPath = `${libraryPathPrefix}/${relativeNewPath}`.replace(/\/\/+/g, '/');

      return {
        bookId: book.id,
        originalPath,
        relativeOriginalPath,
        libraryPathPrefix,
        newPath: fullNewPath,
        relativeNewPath
      };
    });
  }

  get movedFileCount(): number {
    return this.filePreviews.filter(p => p.isMoved).length;
  }

  sanitize(input: string | undefined): string {
    return input?.replace(/[\\/:*?"<>|]/g, '')
      .replace(/[\x00-\x1F\x7F]/g, '')
      .replace(/\s+/g, ' ')
      .trim() ?? '';
  }

  formatYear(dateStr?: string): string {
    if (!dateStr) return '';
    const date = new Date(dateStr);
    return isNaN(date.getTime()) ? '' : date.getFullYear().toString();
  }

  formatSeriesIndex(seriesNumber?: number): string {
    if (seriesNumber == null) return '';
    return this.sanitize(seriesNumber.toString());
  }

  getDefaultPattern(): string {
    return 'e.g. {authors}/<{series}/><{seriesIndex}. >{title} - {authors}< ({year})>';
  }

  saveChanges(): void {
    if (!this.movePattern?.trim()) {
      this.messageService.add({
        severity: 'error',
        summary: 'Missing Pattern',
        detail: 'Move pattern cannot be empty. Please enter a valid pattern.',
        life: 3000
      });
      return;
    }

    this.loading = true;
    const request: FileMoveRequest = {
      bookIds: [...this.bookIds],
      pattern: this.movePattern
    };

    this.fileOperationsService.moveFiles(request).subscribe({
      next: () => {
        this.loading = false;
        this.filePreviews.forEach(p => (p.isMoved = true));
        this.messageService.add({
          severity: 'success',
          summary: 'Files Moved',
          detail: `${this.filePreviews.length} file(s) successfully relocated.`,
          life: 3000
        });
      },
      error: () => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Move Failed',
          detail: 'An error occurred while moving the files.',
          life: 3000
        });
      }
    });
  }

  setDefaultPattern(): void {
    const trimmedPattern = this.movePattern?.trim();

    if (!trimmedPattern) {
      this.messageService.add({
        severity: 'error',
        summary: 'Cannot Save',
        detail: 'Move pattern cannot be empty.'
      });
      return;
    }

    const payload = [
      {
        key: AppSettingKey.MOVE_FILE_PATTERN,
        newValue: trimmedPattern
      }
    ];

    this.appSettingsService.saveSettings(payload).subscribe({
      next: () => this.messageService.add({
        severity: 'success',
        summary: 'Saved',
        detail: 'Move pattern saved as default.'
      }),
      error: () => this.messageService.add({
        severity: 'error',
        summary: 'Error',
        detail: 'Failed to save default move pattern.'
      })
    });
  }

  cancel(): void {
    this.ref.close();
  }
}
