export interface EmailProvider {
  isEditing: boolean;
  id: number;
  name: string;
  host: string;
  port: number;
  username: string;
  password: string;
  auth: boolean;
  startTls: boolean;
}
