import AuthFlow from "@/components/auth-flow";
import { queryValue } from "@/lib/routes";

export default async function RegisterPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[] }>;
}) {
  const params = await searchParams;
  return <AuthFlow mode="register" nextPath={queryValue(params.next)} />;
}
