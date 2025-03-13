import {inject, Injectable} from '@angular/core';
import {HttpClient} from '@angular/common/http';
import {BehaviorSubject, Observable} from 'rxjs';
import {EmailProvider} from '../model/email-provider.model';
import {API_CONFIG} from '../../config/api-config';
import {User} from '../../user.service';

@Injectable({
  providedIn: 'root'
})
export class EmailProviderService {
  private readonly url = `${API_CONFIG.BASE_URL}/api/v1/email/providers`;

  private http = inject(HttpClient);

  getEmailProviders(): Observable<EmailProvider[]> {
    return this.http.get<EmailProvider[]>(this.url);
  }


}
