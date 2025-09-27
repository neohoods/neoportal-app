import { ComponentFixture, TestBed } from '@angular/core/testing';

import { EmailTemplatesComponent } from './email-templates.component';

describe('EmailTemplatesComponent', () => {
    let component: EmailTemplatesComponent;
    let fixture: ComponentFixture<EmailTemplatesComponent>;

    beforeEach(async () => {
        await TestBed.configureTestingModule({
            imports: [EmailTemplatesComponent]
        })
            .compileComponents();

        fixture = TestBed.createComponent(EmailTemplatesComponent);
        component = fixture.componentInstance;
        fixture.detectChanges();
    });

    it('should create', () => {
        expect(component).toBeTruthy();
    });
});
