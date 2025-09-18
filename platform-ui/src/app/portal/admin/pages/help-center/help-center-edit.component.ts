import { BreakpointObserver, Breakpoints } from '@angular/cdk/layout';
import { Component, Inject, signal } from '@angular/core';
import { FormBuilder, FormGroup, FormsModule, ReactiveFormsModule, Validators } from '@angular/forms';
import { TranslateModule } from '@ngx-translate/core';
import { TuiAlertService, TuiButton, TuiDialog, TuiIcon, TuiTextfield } from '@taiga-ui/core';
import { TuiAccordion } from '@taiga-ui/kit';
import { TuiInputModule } from '@taiga-ui/legacy';
import { QuillModule } from 'ngx-quill';
import { UIHelpArticle, UIHelpCategory } from '../../../../models/UIHelp';
import { CUSTOM_PAGES_SERVICE_TOKEN } from '../../admin.providers';
import { CustomPagesService } from '../../services/custom-pages.service';

@Component({
  selector: 'app-faq-edit',
  standalone: true,
  imports: [
    QuillModule,
    ReactiveFormsModule,
    TuiAccordion,
    TuiIcon,
    FormsModule,
    TuiButton,
    TuiDialog,
    TuiInputModule,
    TuiTextfield,
    TranslateModule
  ],
  templateUrl: './help-center-edit.component.html',
  styleUrl: './help-center-edit.component.scss'
})
export class HelpCenterEditComponent {
  // Signals for reactive data
  categoriesPerId = signal<{ [key: string]: UIHelpCategory }>({});
  categories = signal<UIHelpCategory[]>([]);
  articles = signal<UIHelpArticle[]>([]);
  articlesByCategory = signal<{ [key: string]: UIHelpArticle[] }>({});

  selectedCategoryId = signal<string | null>(null);
  searchText = signal('');
  categoryForm: FormGroup;
  articleForm: FormGroup;

  protected openCategoryDialog = signal(false);
  currentCategory = signal<UIHelpCategory | null>(null);
  currentArticle = signal<UIHelpArticle | null>(null);
  openArticleDialog = signal(false);

  editorConfig = {
    toolbar: [
      ['bold', 'italic', 'underline', 'strike'], // Text formatting
      [{ header: 1 }, { header: 2 }], // Headers
      [{ list: 'ordered' }, { list: 'bullet' }], // Lists
      [{ indent: '-1' }, { indent: '+1' }], // Indentation
      [{ align: [] }], // Text alignment
      ['link', 'image'], // Links and images
      ['clean'], // Remove formatting
    ],
  };
  isMobile = signal(false);

  constructor(@Inject(CUSTOM_PAGES_SERVICE_TOKEN) private customPagesService: CustomPagesService,
    private alerts: TuiAlertService,
    private fb: FormBuilder,
    private breakpointObserver: BreakpointObserver) {
    this.categoryForm = this.fb.group({
      name: ['', Validators.required],
      icon: ['', Validators.required],
    });

    this.articleForm = this.fb.group({
      title: ['', Validators.required],
      content: ['', Validators.required],
    });

    this.customPagesService.getCategories().subscribe(categories => {
      const sortedCategories = categories.sort((a, b) => a.order - b.order);
      this.categories.set(sortedCategories);
      this.categoriesPerId.set(categories.reduce((acc, category) => {
        acc[category.id] = category;
        return acc;
      }, {} as { [key: string]: UIHelpCategory }));
      this.selectedCategoryId.set(categories[0].id);
      this.selectCategory(categories[0].id);
    });

    this.breakpointObserver
      .observe([Breakpoints.Handset])
      .subscribe((result) => {
        this.isMobile.set(result.matches);
      });
  }

  selectCategory(categoryId: string) {
    this.selectedCategoryId.set(categoryId);
    const currentArticlesByCategory = this.articlesByCategory();
    if (currentArticlesByCategory[categoryId]) {
      this.articles.set(currentArticlesByCategory[categoryId]);
    } else {
      this.customPagesService.getArticles(categoryId).subscribe(articles => {
        const sortedArticles = articles.sort((a, b) => a.order - b.order);
        this.articles.set(sortedArticles);
        this.articlesByCategory.update(current => ({
          ...current,
          [categoryId]: sortedArticles
        }));
      });
    }
  }

  onTextFilterChange(searchText: string) {
    this.searchText.set(searchText);
    if (searchText.length < 3) {
      return;
    }

    this.selectedCategoryId.set(null);
    const currentCategories = this.categories();
    const currentArticlesByCategory = this.articlesByCategory();

    currentCategories.forEach(category => {
      if (!currentArticlesByCategory[category.id]) {
        this.customPagesService.getArticles(category.id).subscribe(articles => {
          const sortedArticles = articles.sort((a, b) => a.order - b.order);
          this.articlesByCategory.update(current => ({
            ...current,
            [category.id]: sortedArticles
          }));
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
      const categoryArticles = currentArticlesByCategory[categoryId].filter(article =>
        article.title.toLowerCase().includes(searchTextLower) ||
        article.content.toLowerCase().includes(searchTextLower)
      );
      filteredArticles.push(...categoryArticles);
    }
    this.articles.set(filteredArticles);
  }


  protected orderUp(category: UIHelpCategory): void {
    const currentCategories = this.categories();
    const currentIndex = currentCategories.findIndex(cat => cat.id === category.id);
    if (currentIndex > 0) {
      const aboveCategory = currentCategories[currentIndex - 1];
      const currentOrder = category.order;

      // Swap the order values
      category.order = aboveCategory.order;
      aboveCategory.order = currentOrder;
      const sortedCategories = [...currentCategories].sort((a, b) => a.order - b.order);
      this.categories.set(sortedCategories);

      // Update both categories
      this.customPagesService.updateCategory(category).subscribe();
      this.customPagesService.updateCategory(aboveCategory).subscribe();
    }
  }

  protected orderDown(category: UIHelpCategory): void {
    const currentCategories = this.categories();
    const currentIndex = currentCategories.findIndex(cat => cat.id === category.id);
    if (currentIndex < currentCategories.length - 1) {
      const belowCategory = currentCategories[currentIndex + 1];
      const currentOrder = category.order;

      category.order = belowCategory.order;
      belowCategory.order = currentOrder;
      const sortedCategories = [...currentCategories].sort((a, b) => a.order - b.order);
      this.categories.set(sortedCategories);

      this.customPagesService.updateCategory(category).subscribe();
      this.customPagesService.updateCategory(belowCategory).subscribe();
    }
  }

  protected editCategory(category: UIHelpCategory): void {
    this.openCategoryDialog.set(true);
    this.currentCategory.set(category);
    this.categoryForm.patchValue({
      name: category.name,
      icon: category.icon
    });
  }

  protected editArticle(article: UIHelpArticle): void {
    this.openArticleDialog.set(true);
    this.currentArticle.set(article);
    this.articleForm.patchValue({
      title: article.title,
      content: article.content
    });
  }

  addCategory() {
    this.openCategoryDialog.set(true);
    this.currentCategory.set({
      id: 'new',
      name: '',
      icon: '@tui.notebook',
      order: this.categories().length
    });
    this.categoryForm.patchValue({
      name: '',
      icon: '@tui.notebook'
    });
  }


  addArticle() {
    this.openArticleDialog.set(true);
    this.currentArticle.set({
      id: 'new',
      title: '',
      content: '',
      order: this.articles().length,
      category: this.categoriesPerId()[this.selectedCategoryId()!]
    });
    this.articleForm.patchValue({
      title: '',
      content: ''
    });
  }

  setCategory() {
    if (this.categoryForm.valid) {
      const newCategory = this.categoryForm.get('name')?.value;
      const newIcon = this.categoryForm.get('icon')?.value;
      const currentCategory = this.currentCategory();
      if (typeof newCategory === 'string' && currentCategory?.id) {
        if (currentCategory.id === 'new') {
          this.customPagesService.createCategory({ ...currentCategory, name: newCategory, icon: newIcon })
            .subscribe((createdCategory) => {
              this.openCategoryDialog.set(false);
              this.articlesByCategory.update(current => ({
                ...current,
                [createdCategory.id]: []
              }));
              this.categories.update(current => [...current, createdCategory]);
              this.categoriesPerId.update(current => ({
                ...current,
                [createdCategory.id]: createdCategory
              }));
              this.selectCategory(createdCategory.id);
              window.scrollTo({ top: 0, behavior: 'smooth' });

              this.alerts
                .open(
                  'Category <strong>' +
                  currentCategory?.name +
                  '</strong> created successfully',
                  { appearance: 'positive' },
                )
                .subscribe();
            });
        } else {
          this.customPagesService.updateCategory({ ...currentCategory, name: newCategory, icon: newIcon })
            .subscribe((updatedCategory) => {
              this.openCategoryDialog.set(false);
              this.categoriesPerId.update(current => ({
                ...current,
                [currentCategory.id]: updatedCategory
              }));
              this.categories.update(current =>
                current.map(cat => cat.id === currentCategory.id ? updatedCategory : cat)
              );
              window.scrollTo({ top: 0, behavior: 'smooth' });
              this.alerts
                .open(
                  'Category <strong>' +
                  currentCategory?.name +
                  '</strong> edited successfully',
                  { appearance: 'positive' },
                )
                .subscribe();
            });
        }
      }
    }
  }

  setArticle() {
    if (this.articleForm.valid) {
      const newArticle = this.articleForm.value;
      const currentArticle = this.currentArticle();
      const selectedCategoryId = this.selectedCategoryId();

      if (currentArticle?.id === 'new') {
        this.customPagesService.createArticle({ ...newArticle, category: this.categoriesPerId()[selectedCategoryId!] }).subscribe((createdArticle) => {
          this.openArticleDialog.set(false);
          window.scrollTo({ top: 0, behavior: 'smooth' });

          this.articles.update(current => {
            const updated = [...current, createdArticle];
            return updated.sort((a, b) => a.order - b.order);
          });

          // Update articlesByCategory for the current category
          this.articlesByCategory.update(current => ({
            ...current,
            [selectedCategoryId!]: [...(current[selectedCategoryId!] || []), createdArticle].sort((a, b) => a.order - b.order)
          }));

          this.alerts
            .open(
              'Article <strong>' +
              createdArticle.title +
              '</strong> created successfully',
              { appearance: 'positive' },
            )
            .subscribe();
        });
      } else {
        this.customPagesService.updateArticle({ ...currentArticle!, title: newArticle.title, content: newArticle.content }).subscribe((updatedArticle) => {
          this.openArticleDialog.set(false);

          this.articles.update(current => {
            const filtered = current.filter(article => article.id !== currentArticle!.id);
            const updated = [...filtered, updatedArticle];
            return updated.sort((a, b) => a.order - b.order);
          });

          // Update articlesByCategory for the current category
          this.articlesByCategory.update(current => ({
            ...current,
            [selectedCategoryId!]: current[selectedCategoryId!].map(article =>
              article.id === currentArticle!.id ? updatedArticle : article
            ).sort((a, b) => a.order - b.order)
          }));

          window.scrollTo({ top: 0, behavior: 'smooth' });
          this.alerts
            .open(
              'Article <strong>' +
              currentArticle?.title +
              '</strong> edited successfully',
              { appearance: 'positive' },
            )
            .subscribe();
        });
      }
    }
  }

  protected orderUpArticle(article: UIHelpArticle): void {
    const selectedCategoryId = this.selectedCategoryId();
    if (selectedCategoryId) {
      const currentArticlesByCategory = this.articlesByCategory();
      const articles = currentArticlesByCategory[selectedCategoryId];
      const currentIndex = articles.findIndex(art => art.id === article.id);
      if (currentIndex > 0) {
        const aboveArticle = articles[currentIndex - 1];
        const currentOrder = article.order;

        // Swap the order values
        article.order = aboveArticle.order;
        aboveArticle.order = currentOrder;
        const sortedArticles = [...articles].sort((a, b) => a.order - b.order);

        this.articlesByCategory.update(current => ({
          ...current,
          [selectedCategoryId]: sortedArticles
        }));
        this.articles.set(sortedArticles);

        // Update both articles
        this.customPagesService.updateArticle(article).subscribe();
        this.customPagesService.updateArticle(aboveArticle).subscribe();
      }
    }
  }

  protected orderDownArticle(article: UIHelpArticle): void {
    const selectedCategoryId = this.selectedCategoryId();
    if (selectedCategoryId) {
      const currentArticlesByCategory = this.articlesByCategory();
      const articles = currentArticlesByCategory[selectedCategoryId];
      const currentIndex = articles.findIndex(art => art.id === article.id);
      if (currentIndex < articles.length - 1) {
        const belowArticle = articles[currentIndex + 1];
        const currentOrder = article.order;

        // Swap the order values
        article.order = belowArticle.order;
        belowArticle.order = currentOrder;
        const sortedArticles = [...articles].sort((a, b) => a.order - b.order);

        this.articlesByCategory.update(current => ({
          ...current,
          [selectedCategoryId]: sortedArticles
        }));
        this.articles.set(sortedArticles);

        // Update both articles
        this.customPagesService.updateArticle(article).subscribe();
        this.customPagesService.updateArticle(belowArticle).subscribe();
      }
    }
  }
}
