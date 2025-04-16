import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable } from 'rxjs';
import {API_CONFIG} from '../../config/api-config';

export interface AppVersion {
  current: string;
  latest: string;
}

@Injectable({
  providedIn: 'root'
})
export class VersionService {
  private versionUrl = `${API_CONFIG.BASE_URL}/api/v1/version`;

  constructor(private http: HttpClient) {}

  getVersion(): Observable<AppVersion> {
    return this.http.get<AppVersion>(this.versionUrl);
  }
}
