import {Component, inject} from '@angular/core';
import {Tab, TabList, TabPanel, TabPanels, Tabs} from 'primeng/tabs';
import {BookPreferences} from './book-preferences/book-preferences.component';
import {AdminComponent} from './admin/admin.component';
import {UserService} from '../../../user.service';
import {AsyncPipe, NgIf} from '@angular/common';
import {MetadataAdvancedFetchOptionsComponent} from "../../../metadata/metadata-options-dialog/metadata-advanced-fetch-options/metadata-advanced-fetch-options.component";
import {MetadataRefreshOptions} from '../../../metadata/model/request/metadata-refresh-options.model';
import {AppSettingsService} from '../../service/app-settings.service';
import {MetadataSettingsComponent} from './metadata-settings/metadata-settings.component';
import {EmailComponent} from '../email/email.component';

@Component({
  selector: 'app-settings',
  imports: [
    Tabs,
    TabList,
    Tab,
    TabPanels,
    TabPanel,
    BookPreferences,
    AdminComponent,
    NgIf,
    AsyncPipe,
    MetadataSettingsComponent,
    EmailComponent
  ],
  templateUrl: './settings.component.html',
  styleUrl: './settings.component.scss'
})
export class SettingsComponent {

  protected userService = inject(UserService);

}
