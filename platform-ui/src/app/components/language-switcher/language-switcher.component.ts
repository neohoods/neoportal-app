import { NgForOf, TitleCasePipe } from '@angular/common';
import { Component, inject, OnInit } from '@angular/core';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { TranslateService } from '@ngx-translate/core';
import { TUI_DOC_ICONS } from '@taiga-ui/addon-doc/tokens';
import { TuiButton } from '@taiga-ui/core/components/button';
import { TuiDataList, TuiOptGroup, TuiOption } from '@taiga-ui/core/components/data-list';
import { tuiScrollbarOptionsProvider } from '@taiga-ui/core/components/scrollbar';
import { TuiTextfield } from '@taiga-ui/core/components/textfield';
import { TuiDropdown } from '@taiga-ui/core/directives/dropdown';
import { TuiFlagPipe } from '@taiga-ui/core/pipes/flag';
import type { TuiCountryIsoCode, TuiLanguageName } from '@taiga-ui/i18n/types';
import { TuiLanguageSwitcherService } from '@taiga-ui/i18n/utils';
import { TuiBadge } from '@taiga-ui/kit/components/badge';
import { TuiBadgedContent } from '@taiga-ui/kit/components/badged-content';

@Component({
  selector: 'language-switcher',
  imports: [
    NgForOf,
    ReactiveFormsModule,
    TitleCasePipe,
    TuiBadge,
    TuiBadgedContent,
    TuiButton,
    TuiDataList,
    TuiOption,
    TuiOptGroup,
    TuiDropdown,
    TuiFlagPipe,
    TuiTextfield,
  ], templateUrl: './language-switcher.component.html',
  styleUrl: './language-switcher.component.scss',
  providers: [tuiScrollbarOptionsProvider({ mode: 'hover' })],

})
export class LanguageSwitcherComponent implements OnInit {
  protected readonly icons = inject(TUI_DOC_ICONS);
  protected readonly switcher = inject(TuiLanguageSwitcherService);
  protected readonly language = new FormControl('');

  constructor(public translate: TranslateService) {
    this.translate.addLangs(['fr', 'en']);
    this.translate.setDefaultLang('en');
  }

  ngOnInit(): void {
    // Initialize language based on current translation service language
    const currentLang = this.translate.currentLang || this.translate.defaultLang || 'en';
    const languageName = this.getLanguageNameFromCode(currentLang);
    this.language.setValue(capitalize(languageName));

    // Set the UI language switcher to match
    this.switcher.setLanguage(languageName);
  }

  protected open = false;

  public readonly flags = new Map<TuiLanguageName, TuiCountryIsoCode>([
    ['english', 'GB'],
    ['french', 'FR'],
  ]);

  public readonly languageCodes = new Map<TuiLanguageName, string>([
    ['english', 'en'],
    ['french', 'fr'],
  ]);

  public readonly names: TuiLanguageName[] = Array.from(this.flags.keys());

  public setLang(lang: TuiLanguageName): void {
    this.language.setValue(capitalize(lang));
    this.switcher.setLanguage(lang);
    this.open = false;
    const langCode = this.languageCodes.get(lang) ?? 'en';
    this.translate.use(langCode);
  }

  public getLanguageNameFromCode(code: string): TuiLanguageName {
    const entry = Array.from(this.languageCodes.entries()).find(([, c]) => c === code);
    return entry ? entry[0] : 'english';
  }
}

function capitalize(value: string): string {
  return `${value.charAt(0).toUpperCase()}${value.slice(1)}`;
}
