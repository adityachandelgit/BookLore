import {SortOption} from './sort.model';

export interface Library {
  id?: number;
  name: string;
  icon: string;
  watch: boolean;
  sort?: SortOption;
  paths: LibraryPath[];
}

export interface LibraryPath {
  id?: number;
  path: string;
}
