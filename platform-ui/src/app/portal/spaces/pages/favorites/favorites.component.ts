import { CommonModule } from '@angular/common';
import { Component, OnInit, signal } from '@angular/core';
import { Router } from '@angular/router';
import { TuiButton, TuiIcon, TuiLoader } from '@taiga-ui/core';

import { inject } from '@angular/core';
import { Space } from '../../services/spaces.service';
import { SPACES_SERVICE_TOKEN } from '../../spaces.provider';

@Component({
    selector: 'app-favorites',
    standalone: true,
    imports: [
        CommonModule,
        TuiButton,
        TuiIcon,
        TuiLoader
    ],
    templateUrl: './favorites.component.html',
    styleUrls: ['./favorites.component.scss']
})
export class FavoritesComponent implements OnInit {
    private spacesService = inject(SPACES_SERVICE_TOKEN);
    private router = inject(Router);

    favoriteSpaces = signal<Space[]>([]);
    loading = signal(false);

    ngOnInit(): void {
        this.loadFavorites();
    }

    private loadFavorites(): void {
        this.loading.set(true);

        // For now, we'll load all spaces and simulate favorites
        // In a real implementation, there would be a getFavoriteSpaces method
        this.spacesService.getSpaces().subscribe({
            next: (response) => {
                // Simulate some favorites (first 2 spaces)
                this.favoriteSpaces.set(response.content.slice(0, 2));
                this.loading.set(false);
            },
            error: (error) => {
                console.error('Error loading favorites:', error);
                this.loading.set(false);
            }
        });
    }

    onSpaceClick(space: Space): void {
        this.router.navigate(['/spaces/detail', space.id]);
    }

    onReserveClick(space: Space): void {
        this.router.navigate(['/spaces/detail', space.id], {
            queryParams: { action: 'reserve' }
        });
    }

    onRemoveFavorite(space: Space): void {
        // TODO: Implement remove from favorites
        console.log('Remove from favorites:', space.id);
        const currentFavorites = this.favoriteSpaces();
        this.favoriteSpaces.set(currentFavorites.filter(s => s.id !== space.id));
    }

    getSpaceTypeLabel(type: string): string {
        const labels: { [key: string]: string } = {
            'GUEST_ROOM': 'Chambre d\'amis',
            'COMMON_ROOM': 'Salle commune',
            'COWORKING': 'Coworking',
            'PARKING': 'Parking'
        };
        return labels[type] || type;
    }

    getSpaceTypeIcon(type: string): string {
        const icons: { [key: string]: string } = {
            'GUEST_ROOM': '@tui.home',
            'COMMON_ROOM': '@tui.users',
            'COWORKING': '@tui.monitor',
            'PARKING': '@tui.car'
        };
        return icons[type] || '@tui.map-pin';
    }
}
