import AuthFlow from "@/components/auth-flow";
import { queryValue } from "@/lib/routes";

export default async function LoginPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[]; status?: string | string[] }>;
}) {
  const params = await searchParams;
  return (
    <AuthFlow
      mode="login"
      nextPath={queryValue(params.next)}
      status={queryValue(params.status)}
    />
  );
}
