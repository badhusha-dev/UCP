export interface UserPayload {
  id: number;
  sub: string;
  email: string;
  name: string;
  role: string;
  permissions?: string[];
  exp: number;
}

export const AuthService = {
  login: async (email: string, password: string): Promise<string> => {
    const response = await fetch('/api/users/login', {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
      },
      body: JSON.stringify({ email, password }),
    });

    if (!response.ok) {
      let errMsg = 'Login failed';
      try {
        const errorData = await response.json();
        errMsg = errorData.error || errorData.message || errMsg;
      } catch {
        errMsg = `Login failed (${response.status} ${response.statusText})`;
      }
      throw new Error(errMsg);
    }

    const data = await response.json();
    return data.token;
  },
  register: async (email: string, name: string, password: string): Promise<string> => {
    const response = await fetch('/api/users/register', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ email, name, password }),
    });
    if (!response.ok) {
      let errMsg = 'Registration failed';
      try {
        const errorData = await response.json();
        errMsg = errorData.error || errorData.message || errMsg;
      } catch {
        errMsg = `Registration failed (${response.status} ${response.statusText})`;
      }
      throw new Error(errMsg);
    }
    const data = await response.json();
    return data.token;
  }
};
