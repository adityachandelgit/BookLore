import {Component, OnInit, inject} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {TableModule} from 'primeng/table';
import {InputText} from 'primeng/inputtext';
import {Divider} from 'primeng/divider';
import {DynamicDialogConfig, DynamicDialogRef} from 'primeng/dynamicdialog';

import {BookService} from '../book/service/book.service';
import {Book} from '../book/model/book.model';
import {FileOperationsService, FileMoveRequest} from '../file-operations-service';
import {MessageService} from 'primeng/api';

@Component({
  selector: 'app-file-mover-component',
  standalone: true,
  imports: [Button, FormsModule, TableModule, InputText, Divider],
  templateUrl: './file-mover-component.html',
  styleUrl: './file-mover-component.scss'
})
export class FileMoverComponent implements OnInit {
  private config = inject(DynamicDialogConfig);
  private ref = inject(DynamicDialogRef);
  private bookService = inject(BookService);
  private fileOperationsService = inject(FileOperationsService);
  private messageService = inject(MessageService);

  movePattern = '/{authors}/<{series}/><{seriesIndex}. >{title} - {authors}< ({year})>';
  placeholdersVisible = false;
  loading = false;

  bookIds: Set<number> = new Set();
  books: Book[] = [];
  filePreviews: { originalPath: string; newPath: string; isMoved?: boolean }[] = [];

  ngOnInit(): void {
    this.bookIds = this.config.data?.bookIds ?? new Set();
    const ids = Array.from(this.bookIds);
    this.books = this.bookService.getBooksByIdsFromState(ids);
    this.applyPattern();
  }

  applyPattern(): void {
    const formatYear = (dateStr: string | undefined, format: string): string => {
      if (!dateStr) return '';
      const date = new Date(dateStr);
      if (isNaN(date.getTime())) return '';

      const yyyy = date.getFullYear().toString();
      const yy = yyyy.slice(-2);
      const mm = String(date.getMonth() + 1).padStart(2, '0');
      const dd = String(date.getDate()).padStart(2, '0');

      switch (format) {
        case 'yyyy':
          return yyyy;
        case 'yy':
          return yy;
        case 'yyyy-MM-dd':
          return `${yyyy}-${mm}-${dd}`;
        default:
          return yyyy;
      }
    };

    this.filePreviews = this.books.map(book => {
      const meta = book.metadata!;
      const values: Record<string, string> = {
        authors: this.sanitize(meta.authors?.join(', ') || 'Unknown Author'),
        title: this.sanitize(meta.title || 'Untitled'),

        year: this.sanitize(formatYear(meta.publishedDate, 'yyyy')),
        'year:yyyy': this.sanitize(formatYear(meta.publishedDate, 'yyyy')),
        'year:yy': this.sanitize(formatYear(meta.publishedDate, 'yy')),
        'year:yyyy-MM-dd': this.sanitize(formatYear(meta.publishedDate, 'yyyy-MM-dd')),

        series: this.sanitize(meta.seriesName || ''),
        seriesIndex: this.sanitize(meta.seriesTotal != null ? meta.seriesTotal.toString().padStart(2, '0') : ''),
        language: this.sanitize(meta.language || ''),
        publisher: this.sanitize(meta.publisher || ''),
        isbn: this.sanitize(meta.isbn13 || meta.isbn10 || '')
      };

      const fileName = book.fileName ?? '';
      const extension = fileName.match(/\.[^.]+$/)?.[0] ?? '';

      const originalPath = '/' + `${book.fileSubPath ?? ''}/${fileName}`;

      let newPath = this.movePattern.replace(/<([^<>]+)>/g, (_, block) => {
        const placeholders = [...block.matchAll(/{(.*?)}/g)].map(m => m[1]);
        // Only keep block if all placeholders have non-empty, non-whitespace values
        const hasAllValues = placeholders.every(key => values[key]?.trim().length ?? 0 > 0);
        return hasAllValues
          ? block.replace(/{(.*?)}/g, (_: string, key: string) => values[key])
          : '';
      });

      // Replace placeholders outside optional blocks
      newPath = newPath.replace(/{(.*?)}/g, (_, key) => values[key] ?? '');

      return {
        originalPath,
        newPath: newPath + extension
      };
    });
  }

  get movedFileCount(): number {
    return this.filePreviews.filter(p => p.isMoved).length;
  }

  sanitize(input: string | undefined): string {
    if (!input) return '';
    return input
      .replace(/[\\/:*?"<>|]/g, '')     // Remove illegal Windows chars, incl < and >
      .replace(/[\x00-\x1F\x7F]/g, '')  // Remove control chars
      .replace(/\s+/g, ' ')             // Collapse multiple spaces
      .trim();
  }

  saveChanges(): void {
    this.loading = true;

    const request: FileMoveRequest = {
      bookIds: Array.from(this.bookIds),
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
      error: (err) => {
        this.loading = false;
        this.messageService.add({
          severity: 'error',
          summary: 'Move Failed',
          detail: 'An error occurred while moving the files.',
          life: 5000
        });
      }
    });
  }

  cancel(): void {
    this.ref.close();
  }
}
