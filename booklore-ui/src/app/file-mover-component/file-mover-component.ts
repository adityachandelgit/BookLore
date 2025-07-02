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

  movePattern = '/{authors}/<{series}/><{seriesIndex}. >{title} - {authors} <({year})>';
  placeholdersVisible = false;
  loading = false;

  bookIds: Set<number> = new Set();
  books: Book[] = [];
  filePreviews: { originalPath: string; newPath: string }[] = [];

  ngOnInit(): void {
    this.bookIds = this.config.data?.bookIds ?? new Set();
    const ids = Array.from(this.bookIds);
    this.books = this.bookService.getBooksByIdsFromState(ids);
    this.applyPattern();
  }

  applyPattern(): void {
    const formatYear = (dateStr: string | undefined, format: string): string => {
      if (!dateStr) return 'Unknown Year';
      const date = new Date(dateStr);
      if (isNaN(date.getTime())) return 'Unknown Year';

      const yyyy = date.getFullYear().toString();
      const yy = yyyy.slice(-2);
      const mm = String(date.getMonth() + 1).padStart(2, '0');
      const dd = String(date.getDate()).padStart(2, '0');

      switch (format) {
        case 'yyyy': return yyyy;
        case 'yy': return yy;
        case 'yyyy-MM-dd': return `${yyyy}-${mm}-${dd}`;
        default: return yyyy;
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

      console.log(book.filePath)

      const originalPath = '/' + `${book.fileSubPath ?? ''}/${fileName}`;

      // Replace optional blocks like <{series}/>
      let newPath = this.movePattern.replace(/<([^<>]+)>/g, (_, optionalBlock) => {
        const placeholderRegex = /{(.*?)}/g;
        let match;
        let allHaveValues = true;

        while ((match = placeholderRegex.exec(optionalBlock)) !== null) {
          const key = match[1];
          if (!values[key]) {
            allHaveValues = false;
            break;
          }
        }

        return allHaveValues ? optionalBlock : '';
      });

      // Replace placeholders
      newPath = newPath.replace(/{(.*?)}/g, (_, key) => values[key] ?? '');

      return {
        originalPath,
        newPath: newPath + extension
      };
    });
  }

  sanitize(input: string | undefined): string {
    if (!input) return '';
    return input
      .replace(/[\\/:*?"<>|]/g, '')     // Remove illegal Windows chars, incl < and >
      .replace(/[\x00-\x1F\x7F]/g, '') // Remove control chars
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
        this.ref.close(this.filePreviews);
      },
      error: (err) => {
        this.loading = false;
        console.error('File move failed:', err);
        // Optionally: display toast here
      }
    });
  }

  cancel(): void {
    this.ref.close();
  }
}
