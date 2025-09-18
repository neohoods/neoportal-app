import { Injectable } from "@angular/core";
import { Observable } from "rxjs";
import { InfosHubApiService } from "../../../../api-client";
import { UIInfo } from "../../../../models/UIInfos";
import { InfosService } from "../infos.service";

@Injectable({
  providedIn: 'root',
})
export class APIInfosService implements InfosService {
  constructor(private infosApiService: InfosHubApiService) { }

  getInfos(): Observable<UIInfo> {
    return this.infosApiService.getInfos();
  }

  updateInfos(infos: UIInfo): Observable<UIInfo> {
    // Convertir UIInfo vers le format attendu par l'API
    const apiInfos = {
      ...infos,
      nextAGDate: infos.nextAGDate || ''
    };
    return this.infosApiService.updateInfos(apiInfos as any);
  }
}