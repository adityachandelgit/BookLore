import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import { API_CONFIG } from '../../../config/api-config';
import {EmailRecipient} from './email-recipient.model';

@Injectable({
  providedIn: 'root'
})
export class EmailRecipientService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/email/recipients`;

  private http = inject(HttpClient);

  getRecipients(): Observable<EmailRecipient[]> {
    return this.http.get<EmailRecipient[]>(this.url);
  }

  createRecipient(recipient: EmailRecipient): Observable<EmailRecipient> {
    return this.http.post<EmailRecipient>(this.url, recipient);
  }

  updateRecipient(recipient: EmailRecipient): Observable<EmailRecipient> {
    return this.http.put<EmailRecipient>(`${this.url}/${recipient.id}`, recipient);
  }

  deleteRecipient(id: number): Observable<void> {
    return this.http.delete<void>(`${this.url}/${id}`);
  }

  setDefaultRecipient(id: number): Observable<void> {
    return this.http.patch<void>(`${this.url}/${id}/set-default`, {});
  }
}
