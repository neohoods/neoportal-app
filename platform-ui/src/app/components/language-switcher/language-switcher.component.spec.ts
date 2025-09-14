import { ComponentFixture, TestBed } from '@angular/core/testing';
import { TranslateModule, TranslateService } from '@ngx-translate/core';
import { TuiLanguageSwitcherService } from '@taiga-ui/i18n/utils';

import { LanguageSwitcherComponent } from './language-switcher.component';

describe('LanguageSwitcherComponent', () => {
  let component: LanguageSwitcherComponent;
  let fixture: ComponentFixture<LanguageSwitcherComponent>;
  let translateService: TranslateService;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [
        LanguageSwitcherComponent,
        TranslateModule.forRoot()
      ],
      providers: [
        TranslateService,
        TuiLanguageSwitcherService
      ]
    })
      .compileComponents();

    fixture = TestBed.createComponent(LanguageSwitcherComponent);
    component = fixture.componentInstance;
    translateService = TestBed.inject(TranslateService);
    fixture.detectChanges();
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should initialize with default language', () => {
    expect(component.language.value).toBeTruthy();
  });

  it('should switch language when setLang is called', () => {
    component.setLang('french');
    expect(component.language.value).toBe('French');
    expect(translateService.currentLang).toBe('fr');
  });
});
