import { Injectable } from "@angular/core";
import { map, Observable } from "rxjs";
import { ApplicationsHubApiService } from "../../../../api-client";
import { UIApplication } from "../../../../models/UIApplication";
import { ApplicationsService } from "../applications.service";

@Injectable({
  providedIn: 'root',
})
export class APIApplicationsService implements ApplicationsService {
  constructor(private applicationsApiService: ApplicationsHubApiService) { }


  getApplications(): Observable<UIApplication[]> {
    return this.applicationsApiService.getApplications().pipe(
      map(applications => applications.map(application => ({
        ...application
      })))
    );
  }
}