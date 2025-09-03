import { Observable } from 'rxjs';
import { UIApplication } from '../../../models/UIApplication';



export interface ApplicationsService {
  getApplications(): Observable<UIApplication[]>;
}
