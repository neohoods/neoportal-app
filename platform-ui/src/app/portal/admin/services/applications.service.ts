import { Observable } from 'rxjs';
import { UIApplication } from '../../../models/UIApplication';



export interface ApplicationsService {
  getApplications(): Observable<UIApplication[]>;
  getApplication(id: string): Observable<UIApplication>;
  createApplication(application: UIApplication): Observable<UIApplication>;
  updateApplication(id: string, application: UIApplication): Observable<UIApplication>;
  deleteApplication(id: string): Observable<void>;
}
