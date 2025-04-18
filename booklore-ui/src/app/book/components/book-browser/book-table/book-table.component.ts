import {Component, EventEmitter, inject, Input, OnChanges, Output} from '@angular/core';
import {TableModule} from 'primeng/table';
import {NgIf} from '@angular/common';
import {Rating} from 'primeng/rating';
import {FormsModule} from '@angular/forms';
import {Book, BookMetadata} from '../../../model/book.model';
import {SortOption} from '../../../model/sort.model';
import {MetadataDialogService} from '../../../../metadata/service/metadata-dialog.service';
import {UrlHelperService} from '../../../../utilities/service/url-helper.service';
import {Button} from 'primeng/button';
import {BookService} from '../../../service/book.service';
import {MessageService} from 'primeng/api';

@Component({
  selector: 'app-book-table',
  standalone: true,
  templateUrl: './book-table.component.html',
  imports: [
    TableModule,
    NgIf,
    Rating,
    FormsModule,
    Button
  ],
  styleUrl: './book-table.component.scss'
})
export class BookTableComponent implements OnChanges {
  selectedBooks: Book[] = [];
  selectedBookIds = new Set<number>();

  @Output() selectedBooksChange = new EventEmitter<Set<number>>();
  @Input() books: Book[] = [];
  @Input() sortOption: SortOption | null = null;

  protected urlHelper = inject(UrlHelperService);
  private metadataDialogService = inject(MetadataDialogService);
  private bookService = inject(BookService);
  private messageService = inject(MessageService);

  // Hack to set virtual-scroller height
  ngOnChanges() {
    const wrapperElements: HTMLCollection = document.getElementsByClassName('p-virtualscroller');
    Array.prototype.forEach.call(wrapperElements, function (wrapperElement) {
      wrapperElement.style["height"] = 'calc(100vh - 160px)';
    });
  }

  clearSelectedBooks(): void {
    this.selectedBookIds.clear();
    this.selectedBooks = [];
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  onRowSelect(event: any): void {
    this.selectedBookIds.add(event.data.id);
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  onRowUnselect(event: any): void {
    this.selectedBookIds.delete(event.data.id);
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  onHeaderCheckboxToggle(event: any): void {
    if (event.checked) {
      this.selectedBooks = [...this.books];
      this.selectedBookIds = new Set(this.books.map(book => book.id));
    } else {
      this.clearSelectedBooks();
    }
    this.selectedBooksChange.emit(this.selectedBookIds);
  }

  openMetadataCenter(id: number): void {
    this.metadataDialogService.openBookMetadataCenterDialog(id, 'view');
  }

  getStarColor(rating: number): string {
    if (rating >= 4.5) {
      return 'rgb(34, 197, 94)';
    } else if (rating >= 4) {
      return 'rgb(52, 211, 153)';
    } else if (rating >= 3.5) {
      return 'rgb(234, 179, 8)';
    } else if (rating >= 2.5) {
      return 'rgb(249, 115, 22)';
    } else {
      return 'rgb(239, 68, 68)';
    }
  }

  getAuthorNames(authors: string[]): string {
    return authors?.join(', ') || '';
  }

  getGenres(genres: string[]) {
    return genres?.join(', ') || '';
  }

  toggleMetadataLock(metadata: BookMetadata): void {
    const lockAction = metadata.allFieldsLocked ? 'UNLOCK' : 'LOCK';
    this.bookService.toggleAllLock(new Set([metadata.bookId]), lockAction).subscribe({
      next: () => {
        this.messageService.add({
          severity: 'success',
          summary: `Metadata ${lockAction === 'LOCK' ? 'Locked' : 'Unlocked'}`,
          detail: `Book metadata has been ${lockAction === 'LOCK' ? 'locked' : 'unlocked'} successfully.`,
        });
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: `Failed to ${lockAction === 'LOCK' ? 'Lock' : 'Unlock'}`,
          detail: `An error occurred while ${lockAction === 'LOCK' ? 'locking' : 'unlocking'} the metadata.`,
        });
      }
    });
  }
}
