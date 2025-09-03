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
  getApplication(id: string): Observable<UIApplication> {
    return this.applicationsApiService.getApplication(id).pipe(
      map(application => ({
        ...application
      }))
    );
  }
  createApplication(application: UIApplication): Observable<UIApplication> {
    return this.applicationsApiService.createApplication(application);
  }
  updateApplication(id: string, application: UIApplication): Observable<UIApplication> {
    return this.applicationsApiService.updateApplication(id, application);
  }
  deleteApplication(id: string): Observable<void> {
    return this.applicationsApiService.deleteApplication(id);
  }
}