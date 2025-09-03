import { Injectable } from '@angular/core';
import { Observable, of } from 'rxjs';
import infosData from '../../../../mock/infos.json';
import { UIInfo } from '../../../../models/UIInfos';
import { InfosService } from '../infos.service';

@Injectable({
  providedIn: 'root',
})
export class MockInfosService implements InfosService {

  getInfos(): Observable<UIInfo> {
    return of(infosData as UIInfo);
  }
  updateInfos(infos: UIInfo): Observable<UIInfo> {
    return of(infos);
  }
}
