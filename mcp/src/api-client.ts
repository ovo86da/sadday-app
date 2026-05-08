import axios from "axios";

if (!process.env.SADDAY_API_URL) {
  throw new Error("Variable de entorno SADDAY_API_URL no definida");
}
if (!process.env.SADDAY_API_KEY) {
  throw new Error("Variable de entorno SADDAY_API_KEY no definida");
}

export const apiClient = axios.create({
  baseURL: process.env.SADDAY_API_URL,
  headers: {
    "X-Api-Key": process.env.SADDAY_API_KEY,
    "Content-Type": "application/json",
  },
  timeout: 15_000,
});
