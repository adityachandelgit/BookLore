import {Component, inject, OnInit} from '@angular/core';
import {FormsModule} from '@angular/forms';
import {TableModule} from 'primeng/table';
import {Button} from 'primeng/button';
import {NgIf} from '@angular/common';
import {MessageService} from 'primeng/api';
import {EmailProvider} from '../../model/email-provider.model';
import {EmailProviderService} from '../../service/email-provider.service';

@Component({
  selector: 'app-email',
  imports: [
    FormsModule,
    TableModule,
    Button,
    NgIf
  ],
  templateUrl: './email.component.html',
  styleUrls: ['./email.component.scss'],
})
export class EmailComponent implements OnInit {
  emailProviders: EmailProvider[] = [];
  editingProviderIds: number[] = [];

  private emailProvidersService = inject(EmailProviderService);
  private messageService = inject(MessageService);

  ngOnInit(): void {
    this.loadEmailProviders();
  }

  loadEmailProviders(): void {
    this.emailProvidersService.getEmailProviders().subscribe({
      next: (emailProviders: EmailProvider[]) => {
        this.emailProviders = emailProviders.map((provider) => ({
          ...provider,
          isEditing: false,
        }));
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to load Email Providers',
        });
      },
    });
  }

  toggleEdit(provider: EmailProvider): void {
    provider.isEditing = !provider.isEditing;
    if (provider.isEditing) {
      this.editingProviderIds.push(provider.id);
    } else {
      this.editingProviderIds = this.editingProviderIds.filter((id) => id !== provider.id);
    }
  }

  saveProvider(provider: EmailProvider): void {
    /*this.emailProvidersService.updateProvider(provider).subscribe({
      next: () => {
        provider.isEditing = false;
        this.messageService.add({
          severity: 'success',
          summary: 'Success',
          detail: 'Provider updated successfully',
        });
        this.loadEmailProviders();
      },
      error: () => {
        this.messageService.add({
          severity: 'error',
          summary: 'Error',
          detail: 'Failed to update provider',
        });
      },
    });*/
  }

  deleteProvider(provider: EmailProvider): void {
    /*if (confirm(`Are you sure you want to delete provider "${provider.name}"?`)) {
      this.emailProvidersService.deleteProvider(provider.id).subscribe({
        next: () => {
          this.messageService.add({
            severity: 'success',
            summary: 'Success',
            detail: `Provider "${provider.name}" deleted successfully`,
          });
          this.loadEmailProviders();
        },
        error: () => {
          this.messageService.add({
            severity: 'error',
            summary: 'Error',
            detail: 'Failed to delete provider',
          });
        },
      });
    }*/
  }
}
