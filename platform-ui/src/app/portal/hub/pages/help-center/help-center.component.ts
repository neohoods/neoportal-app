import { ChangeDetectorRef, Component, ElementRef, Inject, OnInit, ViewChild, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TuiIcon } from '@taiga-ui/core';
import { TuiAccordion } from '@taiga-ui/kit';
import { UIHelpArticle, UIHelpCategory } from '../../../../models/UIHelp';
import { HELP_SERVICE_TOKEN } from '../../hub.provider';
import { HelpService } from '../../services/help.service';

@Component({
  selector: 'app-help-center',
  imports: [
    TuiAccordion,
    TuiIcon,
    FormsModule,
    TranslateModule
  ],

  templateUrl: './help-center.component.html',
  styleUrl: './help-center.component.scss'
})
export class HelpCenterComponent implements OnInit {
  categoriesPerId = signal<{ [key: string]: UIHelpCategory }>({});
  categories = signal<UIHelpCategory[]>([]);
  articles = signal<UIHelpArticle[]>([]);
  articlesByCategory = signal<{ [key: string]: UIHelpArticle[] }>({});

  selectedCategoryId = signal<string | null>(null);
  searchText = signal<string>('');

  @ViewChild('mainElement') mainElement!: ElementRef;

  constructor(
    @Inject(HELP_SERVICE_TOKEN) private helpService: HelpService,
    private cdr: ChangeDetectorRef
  ) { }

  ngOnInit() {
    this.loadCategories();
  }

  private loadCategories() {
    this.helpService.getCategories().subscribe(categories => {
      const sortedCategories = categories.sort((a, b) => a.order - b.order);
      this.categories.set(sortedCategories);

      const categoriesById = sortedCategories.reduce((acc, category) => {
        acc[category.id] = category;
        return acc;
      }, {} as { [key: string]: UIHelpCategory });
      this.categoriesPerId.set(categoriesById);

      if (sortedCategories.length > 0) {
        const firstCategoryId = sortedCategories[0].id;
        this.selectedCategoryId.set(firstCategoryId);
        this.selectCategory(firstCategoryId);
      }
      window.scrollTo({ top: 0, behavior: 'smooth' });
      this.cdr.detectChanges();
    });
  }

  selectCategory(categoryId: string) {
    this.selectedCategoryId.set(categoryId);
    const currentArticlesByCategory = this.articlesByCategory();

    if (currentArticlesByCategory[categoryId]) {
      this.articles.set(currentArticlesByCategory[categoryId]);
    } else {
      this.helpService.getArticles(categoryId).subscribe(articles => {
        const sortedArticles = articles.sort((a, b) => a.order - b.order);
        this.articles.set(sortedArticles);

        const updatedArticlesByCategory = { ...currentArticlesByCategory };
        updatedArticlesByCategory[categoryId] = sortedArticles;
        this.articlesByCategory.set(updatedArticlesByCategory);
        window.scrollTo({ top: 0, behavior: 'smooth' });

        this.cdr.detectChanges();
      });
    }
    //this.scrollToMain();
  }

  private scrollToMain() {
    if (this.mainElement) {
      this.mainElement.nativeElement.scrollIntoView({ behavior: 'smooth' });
    }
  }

  onTextFilterChange(event: Event) {
    const searchText = (event.target as HTMLInputElement)?.value || '';
    this.searchText.set(searchText);

    if (searchText.length < 3) {
      // Reset to selected category if search is too short
      const currentSelectedId = this.selectedCategoryId();
      if (currentSelectedId) {
        this.selectCategory(currentSelectedId);
      }
      return;
    }

    this.selectedCategoryId.set(null);
    const categories = this.categories();
    const currentArticlesByCategory = this.articlesByCategory();

    // Load articles for categories that haven't been loaded yet
    categories.forEach(category => {
      if (!currentArticlesByCategory[category.id]) {
        this.helpService.getArticles(category.id).subscribe(articles => {
          const sortedArticles = articles.sort((a, b) => a.order - b.order);
          const updatedArticlesByCategory = { ...this.articlesByCategory() };
          updatedArticlesByCategory[category.id] = sortedArticles;
          this.articlesByCategory.set(updatedArticlesByCategory);

          // Re-filter after loading new articles
          this.filterArticles();
        });
      }
    });

    this.filterArticles();
  }

  private filterArticles() {
    const searchTextLower = this.searchText().toLowerCase();
    const currentArticlesByCategory = this.articlesByCategory();
    const filteredArticles: UIHelpArticle[] = [];

    for (const categoryId in currentArticlesByCategory) {
      const articles = currentArticlesByCategory[categoryId].filter(article =>
        article.title.toLowerCase().includes(searchTextLower) ||
        article.content.toLowerCase().includes(searchTextLower)
      );
      filteredArticles.push(...articles);
    }

    this.articles.set(filteredArticles);
    this.cdr.detectChanges();
  }

}
