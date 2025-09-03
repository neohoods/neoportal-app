import { Injectable } from "@angular/core";
import { Observable, of } from "rxjs";
import { loadApplicationsData } from "../../../../mock/applications-loader";
import { UIApplication } from "../../../../models/UIApplication";
import { ApplicationsService } from "../applications.service";

@Injectable({
  providedIn: 'root',
})
export class MockApplicationsService implements ApplicationsService {

  getApplications(): Observable<UIApplication[]> {
    return of(loadApplicationsData());
  }
  getApplication(id: string): Observable<UIApplication> {
    return of(loadApplicationsData().find(application => application.id === id) as UIApplication);
  }
  createApplication(application: UIApplication): Observable<UIApplication> {
    return of(application);
  }
  updateApplication(id: string, application: UIApplication): Observable<UIApplication> {
    return of(application);
  }
  deleteApplication(id: string): Observable<void> {
    return of(undefined);
  }
}