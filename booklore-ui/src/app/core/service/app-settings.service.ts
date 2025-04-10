import {Injectable} from '@angular/core';
import {HttpClient, HttpParams} from '@angular/common/http';
import {BehaviorSubject} from 'rxjs';
import {AppSettings} from '../model/app-settings.model';
import {API_CONFIG} from '../../config/api-config';

@Injectable({
  providedIn: 'root'
})
export class AppSettingsService {
  private readonly apiUrl = `${API_CONFIG.BASE_URL}/api/v1/settings`;

  private appSettingsSubject = new BehaviorSubject<AppSettings | null>(null);
  appSettings$ = this.appSettingsSubject.asObservable();

  constructor(private http: HttpClient) {
    this.loadAppSettings();
  }

  loadAppSettings(): void {
    this.http.get<AppSettings>(this.apiUrl).subscribe({
      next: (settings: AppSettings) => {
        this.appSettingsSubject.next(settings);
      },
      error: (error) => {
        console.error('Error loading app settings:', error);
        this.appSettingsSubject.next(null);
      }
    });
  }

  saveAppSetting(category: string, key: string, newValue: any): void {
    const payload = {
      category,
      name: key,
      value: newValue
    };
    this.http.put(this.apiUrl, payload).subscribe({
      next: () => {
        console.log('Settings saved successfully');
      },
      error: (error) => {
        console.error('Error saving setting:', error);
      }
    });
  }
}
