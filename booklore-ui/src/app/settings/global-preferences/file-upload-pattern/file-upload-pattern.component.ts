import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {Button} from 'primeng/button';
import {MessageService} from 'primeng/api';
import {AppSettingsService} from '../../../core/service/app-settings.service';
import {Observable} from 'rxjs';
import {AppSettingKey, AppSettings} from '../../../core/model/app-settings.model';
import {filter, take} from 'rxjs/operators';
import {Divider} from 'primeng/divider';
import {Tooltip} from 'primeng/tooltip';

@Component({
  selector: 'app-file-upload-pattern',
  templateUrl: './file-upload-pattern.component.html',
  standalone: true,
  imports: [FormsModule, Button, Divider, Tooltip],
  styleUrls: ['./file-upload-pattern.component.scss'],
})
export class FileUploadPatternComponent implements OnInit {
  readonly exampleMetadata: Record<string, string> = {
    title: 'The Fellowship of the Ring',
    authors: 'J.R.R. Tolkien',
    year: '1954',
    series: 'The Lord of the Rings',
    seriesIndex: '01',
    language: 'en',
    publisher: 'Allen & Unwin',
    isbn: '9780618574940',
  };

  uploadPattern = '';
  movePattern = '';

  uploadErrorMessage = '';
  moveErrorMessage = '';

  private appSettingsService = inject(AppSettingsService);
  private messageService = inject(MessageService);

  appSettings$: Observable<AppSettings | null> = this.appSettingsService.appSettings$;

  ngOnInit(): void {
    this.appSettings$
      .pipe(filter((settings) => settings != null), take(1))
      .subscribe((settings) => {
        this.uploadPattern = settings?.uploadPattern ?? '';
        this.movePattern = settings?.movePattern ?? '';
      });
  }

  private replacePlaceholders(pattern: string, values: Record<string, string>): string {
    pattern = pattern.replace(/<([^<>]+)>/g, (_, block) => {
      const placeholders = [...block.matchAll(/{(.*?)}/g)].map((m) => m[1]);
      const allHaveValues = placeholders.every((key) => values[key]?.trim());
      return allHaveValues
        ? block.replace(/{(.*?)}/g, (_: string, key: string) => values[key] ?? '')
        : '';
    });
    return pattern.replace(/{(.*?)}/g, (_, key) => values[key] ?? '').trim();
  }

  private appendExtensionIfMissing(path: string, ext = '.pdf'): string {
    const lastSegment = path.split('/').pop() ?? '';
    const hasExtension = /\.[a-z0-9]{2,5}$/i.test(lastSegment);
    return hasExtension ? path : path + ext;
  }

  generateUploadPreview(): string {
    let path = this.replacePlaceholders(this.uploadPattern || '', this.exampleMetadata);

    if (!path) return '/original_filename.pdf';

    // If pattern ends with slash, append original filename
    if (path.endsWith('/')) {
      return path + 'original_filename.pdf';
    }

    // Replace {originalFilename} placeholder if present
    if (path.includes('{originalFilename}')) {
      path = path.replace('{originalFilename}', 'original_filename.pdf');
      return path.startsWith('/') ? path : `/${path}`;
    }

    path = this.appendExtensionIfMissing(path);
    return path.startsWith('/') ? path : `/${path}`;
  }

  generateMovePreview(): string {
    let path = this.replacePlaceholders(this.movePattern || '', this.exampleMetadata);

    if (!path) return '/original_filename.pdf';

    if (path.endsWith('/')) {
      return path + 'original_filename.pdf';
    }

    if (path.includes('{originalFilename}')) {
      path = path.replace('{originalFilename}', 'original_filename.pdf');
      return path.startsWith('/') ? path : `/${path}`;
    }

    path = this.appendExtensionIfMissing(path);
    return path.startsWith('/') ? path : `/${path}`;
  }

  validatePattern(pattern: string): boolean {
    // Allow letters, numbers, whitespace, common punctuation and placeholders syntax
    const validPatternRegex = /^[\w\s\-{}\/().<>.,:'"]*$/;
    return validPatternRegex.test(pattern);
  }

  onUploadPatternChange(pattern: string): void {
    this.uploadPattern = pattern;
    this.uploadErrorMessage = this.validatePattern(pattern) ? '' : 'Pattern contains invalid characters.';
  }

  onMovePatternChange(pattern: string): void {
    this.movePattern = pattern;
    this.moveErrorMessage = this.validatePattern(pattern) ? '' : 'Pattern contains invalid characters.';
  }

  savePatterns(): void {
    if (this.uploadErrorMessage || this.moveErrorMessage) {
      this.showMessage('error', 'Invalid Pattern', 'Please fix errors before saving.');
      return;
    }
    this.appSettingsService
      .saveSettings([
        {key: AppSettingKey.UPLOAD_FILE_PATTERN, newValue: this.uploadPattern},
        {key: AppSettingKey.MOVE_FILE_PATTERN, newValue: this.movePattern},
      ])
      .subscribe({
        next: () => this.showMessage('success', 'Settings Saved', 'The patterns were successfully saved!'),
        error: () => this.showMessage('error', 'Error', 'There was an error saving the settings.'),
      });
  }

  private showMessage(severity: 'success' | 'error', summary: string, detail: string): void {
    this.messageService.add({severity, summary, detail});
  }
}
