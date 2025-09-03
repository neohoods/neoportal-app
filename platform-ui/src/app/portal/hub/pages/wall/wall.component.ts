import { Component, OnInit } from '@angular/core';
import { AnnouncementsComponent } from '../../components/announcements/announcements.component';
import { InfosComponent } from '../../components/infos/infos.component';

@Component({
  selector: 'app-wall',
  imports: [AnnouncementsComponent, InfosComponent],
  templateUrl: './wall.component.html',
  styleUrl: './wall.component.scss'
})
export class WallComponent implements OnInit {

  ngOnInit(): void {
    // Wall component initialization
    // Child components will handle their own data loading
  }
}
