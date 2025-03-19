import {inject, Injectable} from '@angular/core';
import {API_CONFIG} from '../../config/api-config';
import {HttpClient} from '@angular/common/http';
import {Observable} from 'rxjs';

@Injectable({
  providedIn: 'root'
})
export class EmailService {

  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/emails`;

  private http = inject(HttpClient);

  emailBook(request: { bookId: number, providerId: number, recipientId: number }): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/send-book`, request);
  }

  emailBookQuick(bookId: number): Observable<void> {
    return this.http.post<void>(`${this.apiUrl}/send-book/${bookId}`, {});
  }
}
