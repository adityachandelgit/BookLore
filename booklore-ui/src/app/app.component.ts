import {Component, inject, OnInit} from '@angular/core';
import {RxStompService} from './shared/websocket/rx-stomp.service';
import {Message} from '@stomp/stompjs';
import {BookService} from './book/service/book.service';
import {EventService} from './shared/websocket/event.service';
import {parseLogNotification} from './shared/websocket/model/log-notification.model';
import {ConfirmDialog} from 'primeng/confirmdialog';
import {Toast} from 'primeng/toast';
import {RouterOutlet} from '@angular/router';
import {AuthInitializationService} from './auth-initialization-service';
import {AppConfigService} from './core/service/app-config.service';

@Component({
  selector: 'app-root',
  templateUrl: './app.component.html',
  styleUrl: './app.component.scss',
  standalone: true,
  imports: [ConfirmDialog, Toast, RouterOutlet]
})
export class AppComponent implements OnInit {

  loading = true;
  private authInit = inject(AuthInitializationService);
  private bookService = inject(BookService);
  private rxStompService = inject(RxStompService);
  private eventService = inject(EventService);
  private appConfigService = inject(AppConfigService);

  ngOnInit(): void {

    this.authInit.initialized$.subscribe(ready => {
      this.loading = !ready;
    });

    this.rxStompService.watch('/topic/book-add').subscribe((message: Message) => {
      this.bookService.handleNewlyCreatedBook(JSON.parse(message.body));
    });

    this.rxStompService.watch('/topic/books-remove').subscribe((message: Message) => {
      this.bookService.handleRemovedBookIds(JSON.parse(message.body));
    });

    this.rxStompService.watch('/topic/book-metadata-update').subscribe((message: Message) => {
      this.bookService.handleBookUpdate(JSON.parse(message.body));
    });

    this.rxStompService.watch('/topic/book-metadata-batch-update').subscribe((message: Message) => {
      const updatedBooks = JSON.parse(message.body);
      this.bookService.handleMultipleBookUpdates(updatedBooks);
    });

    this.rxStompService.watch('/topic/log').subscribe((message: Message) => {
      const logNotification = parseLogNotification(message.body);
      this.eventService.handleIncomingLog(logNotification);
    });
  }

  sendMessage() {
    const message = `Message generated at ${new Date()}`;
    this.rxStompService.publish({destination: '/app/send', body: message});
  }
}
