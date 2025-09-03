export interface UICustomPage {
    id: string;
    ref: string;
    order: number;
    position: 'footer-links' | 'copyright' | 'footer-help';
    title: string;
    content: string;
}
