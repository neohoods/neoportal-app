import { Component, Inject, input, OnChanges, OnInit, SimpleChanges } from '@angular/core';
import {
  FormBuilder,
  FormGroup,
  FormsModule,
  ReactiveFormsModule,
  Validators,
} from '@angular/forms';
import { ActivatedRoute, Router } from '@angular/router';
import { TranslateModule } from '@ngx-translate/core';
import { TuiButton, TuiLabel, TuiTextfield } from '@taiga-ui/core';
import {
  TUI_DEFAULT_INPUT_COLORS,
  TuiInputColorModule,
  TuiSelectModule,
} from '@taiga-ui/legacy';
import { QuillModule } from 'ngx-quill';
import { UICustomPage } from '../../../../models/UICustomPage';
import { CUSTOM_PAGES_SERVICE_TOKEN } from '../../../admin/admin.providers';
import { CustomPagesService } from '../../../admin/services/custom-pages.service';

@Component({
  selector: 'custom-pages-edit',
  imports: [
    QuillModule,
    TuiInputColorModule,
    TuiTextfield,
    TuiLabel,
    FormsModule,
    ReactiveFormsModule,
    TuiButton,
    TuiSelectModule,
    TranslateModule
  ],
  templateUrl: './custom-pages-edit.component.html',
  styleUrl: './custom-pages-edit.component.scss'
})
export class CustomPagesEditComponent implements OnInit, OnChanges {
  pageForm: FormGroup;
  protected readonly palette = TUI_DEFAULT_INPUT_COLORS;

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
  page = input<UICustomPage>();

  protected items = [
    'footer-links',
    'copyright',
    'footer-help',
  ];

  constructor(
    private route: ActivatedRoute,
    private fb: FormBuilder,
    private router: Router,
    @Inject(CUSTOM_PAGES_SERVICE_TOKEN) private customPagesService: CustomPagesService,
  ) {
    this.pageForm = this.fb.group({
      ref: ['', Validators.required],
      position: ['', Validators.required],
      title: ['', Validators.required],
      content: ['', Validators.required],
    });
  }

  ngOnInit(): void {
    this.updateForm();
  }

  ngOnChanges(changes: SimpleChanges): void {
    if (changes['page']) {
      this.updateForm();
    }
  }

  updateForm(): void {
    if (this.page()) {
      this.pageForm.patchValue({
        ref: this.page()!.ref,
        position: this.page()!.position,
        title: this.page()!.title,
        content: this.page()!.content,
      });
    } else {
      this.pageForm.patchValue({
        ref: '',
        title: '',
        content: '',
      });
    }
  }

  onSubmit() {
    if (this.pageForm.valid) {
      const newPage: UICustomPage = this.pageForm.value;
      if (this.page()?.id) {
        this.customPagesService.updateCustomPage({
          ...newPage,
          id: this.page()!.id,
        });
      } else {
        this.customPagesService.createCustomPage({
          ...newPage,
          id: this.page()!.id,
        });
      }
      this.router.navigate(['/hub/']);
    }
  }
}

