import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router } from '@angular/router';
import { of } from 'rxjs';
import { AuthService } from '../../../../../services/auth.service';
import { ConfigService } from '../../../../../services/config.service';
import { SignInComponent } from './sign-in.component';

describe('SignInComponent', () => {
  let component: SignInComponent;
  let fixture: ComponentFixture<SignInComponent>;
  let mockAuthService: jasmine.SpyObj<AuthService>;
  let mockConfigService: jasmine.SpyObj<ConfigService>;
  let mockRouter: jasmine.SpyObj<Router>;

  beforeEach(async () => {
    const authServiceSpy = jasmine.createSpyObj('AuthService', ['signIn', 'generateSSOLoginUrl', 'hasRole']);
    const configServiceSpy = jasmine.createSpyObj('ConfigService', ['getSettings']);
    const routerSpy = jasmine.createSpyObj('Router', ['navigate']);

    await TestBed.configureTestingModule({
      imports: [SignInComponent],
      providers: [
        { provide: AuthService, useValue: authServiceSpy },
        { provide: ConfigService, useValue: configServiceSpy },
        { provide: Router, useValue: routerSpy },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(SignInComponent);
    component = fixture.componentInstance;
    mockAuthService = TestBed.inject(AuthService) as jasmine.SpyObj<AuthService>;
    mockConfigService = TestBed.inject(ConfigService) as jasmine.SpyObj<ConfigService>;
    mockRouter = TestBed.inject(Router) as jasmine.SpyObj<Router>;
  });

  it('should create', () => {
    expect(component).toBeTruthy();
  });

  it('should auto-redirect to SSO when SSO is enabled', () => {
    // Arrange
    const mockSettings = { isRegistrationEnabled: true, ssoEnabled: true };
    mockConfigService.getSettings.and.returnValue(mockSettings);
    mockAuthService.generateSSOLoginUrl.and.returnValue(of('https://sso.example.com/login'));

    // Act
    component.ngOnInit();

    // Assert
    expect(mockAuthService.generateSSOLoginUrl).toHaveBeenCalled();
  });

  it('should not auto-redirect when SSO is disabled', () => {
    // Arrange
    const mockSettings = { isRegistrationEnabled: true, ssoEnabled: false };
    mockConfigService.getSettings.and.returnValue(mockSettings);

    // Act
    component.ngOnInit();

    // Assert
    expect(mockAuthService.generateSSOLoginUrl).not.toHaveBeenCalled();
  });
});
