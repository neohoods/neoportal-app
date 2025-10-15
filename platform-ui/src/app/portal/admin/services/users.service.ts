import { Observable } from 'rxjs';
import { UIUser } from '../../../models/UIUser';



export interface UsersService {
  getUsers(): Observable<UIUser[]>;
  getUser(id: string): Observable<UIUser>;
  getUsersByIds(userIds: string[]): Observable<UIUser[]>;
  saveUser(user: UIUser): Observable<UIUser>;
  setUserPassword(userId: string, newPassword: string): Observable<void>;
  deleteUser(id: string): Observable<void>;
}
