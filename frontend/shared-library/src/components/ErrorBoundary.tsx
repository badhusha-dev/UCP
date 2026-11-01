import { Component, type ErrorInfo, type ReactNode } from 'react';
import { reportClientError, addBreadcrumb } from '../services/ErrorReporter';

interface Props {
  children: ReactNode;
  serviceName?: string;
  fallback?: ReactNode;
}

interface State {
  hasError: boolean;
  error: Error | null;
}

export class ErrorBoundary extends Component<Props, State> {
  constructor(props: Props) {
    super(props);
    this.state = { hasError: false, error: null };
  }

  static getDerivedStateFromError(error: Error): State {
    return { hasError: true, error };
  }

  componentDidCatch(error: Error, errorInfo: ErrorInfo): void {
    addBreadcrumb('react.error_boundary', error.message, { componentStack: errorInfo.componentStack });
    reportClientError({
      serviceName: this.props.serviceName || 'shell-app',
      errorType: error.name || 'ReactErrorBoundaryException',
      message: error.message,
      stackTrace: (error.stack || '') + '\nComponent Stack:' + (errorInfo.componentStack || ''),
      severity: 'CRITICAL',
    });
  }

  handleRetry = () => {
    this.setState({ hasError: false, error: null });
  };

  render(): ReactNode {
    if (this.state.hasError) {
      if (this.props.fallback) {
        return this.props.fallback;
      }
      return (
        <div style={{ padding: '32px', display: 'flex', justifyContent: 'center', alignItems: 'center', minHeight: '300px' }}>
          <div style={{ padding: '32px', maxWidth: '500px', backgroundColor: '#111827', border: '1px solid #1F2937', borderRadius: '12px', textAlign: 'center', color: '#F8FAFC' }}>
            <h3 style={{ margin: '0 0 8px 0', color: '#EF4444', fontWeight: 800 }}>
              Something went wrong
            </h3>
            <p style={{ margin: '0 0 24px 0', color: '#94A3B8', fontSize: '14px' }}>
              An unexpected application error has been safely captured by the Error Stack service for diagnosis.
            </p>
            <button
              onClick={this.handleRetry}
              style={{
                backgroundColor: '#4F46E5',
                color: '#FFFFFF',
                border: 'none',
                padding: '10px 20px',
                borderRadius: '8px',
                fontWeight: 700,
                cursor: 'pointer',
              }}
            >
              Try Again
            </button>
          </div>
        </div>
      );
    }
    return this.props.children;
  }
}
