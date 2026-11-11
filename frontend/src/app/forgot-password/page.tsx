import AuthFlow from "@/components/auth-flow";
import { queryValue } from "@/lib/routes";

export default async function ForgotPasswordPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const params = await searchParams;
  return <AuthFlow mode="forgot" nextPath={queryValue(params.next)} />;
}
