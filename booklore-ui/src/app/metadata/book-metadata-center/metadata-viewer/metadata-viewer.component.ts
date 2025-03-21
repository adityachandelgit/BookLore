import {Component, inject} from '@angular/core';
import {Button} from 'primeng/button';
import {AsyncPipe, NgForOf, NgIf} from '@angular/common';
import {Observable} from 'rxjs';
import {BookService} from '../../../book/service/book.service';
import {BookMetadataCenterService} from '../book-metadata-center.service';
import {Rating} from 'primeng/rating';
import {FormsModule} from '@angular/forms';
import {Tag} from 'primeng/tag';
import {BookMetadata} from '../../../book/model/book.model';
import {Divider} from 'primeng/divider';
import {UrlHelperService} from '../../../utilities/service/url-helper.service';
import {UserService} from '../../../user.service';

@Component({
  selector: 'app-metadata-viewer',
  standalone: true,
  templateUrl: './metadata-viewer.component.html',
  styleUrl: './metadata-viewer.component.scss',
  imports: [Button, NgForOf, NgIf, AsyncPipe, Rating, FormsModule, Tag, Divider]
})
export class MetadataViewerComponent {

  private bookService = inject(BookService);
  private metadataCenterService = inject(BookMetadataCenterService);
  protected urlHelper = inject(UrlHelperService);
  protected userService = inject(UserService);

  metadata$: Observable<BookMetadata | null> = this.metadataCenterService.currentMetadata$;

  readBook(bookId: number): void {
    this.bookService.readBook(bookId);
  }

  closeDialog(): void {
    this.metadataCenterService.closeDialog(true);
  }

  getAuthorNames(authors: string[]): string {
    return authors.join(', ');
  }

  download(bookId: number) {
    this.bookService.downloadFile(bookId);
  }
}
