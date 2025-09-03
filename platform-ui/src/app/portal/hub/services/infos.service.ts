import { Observable } from 'rxjs';
import { UIInfo } from '../../../models/UIInfos';



export interface InfosService {
  getInfos(): Observable<UIInfo>;
  updateInfos(infos: UIInfo): Observable<UIInfo>;
}
