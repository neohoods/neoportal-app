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
}