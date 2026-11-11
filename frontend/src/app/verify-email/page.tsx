import AuthFlow from "@/components/auth-flow";
import { queryValue } from "@/lib/routes";

export default async function VerifyEmailPage({
  searchParams,
}: {
  searchParams: Promise<{ next?: string | string[]; token?: string | string[] }>;
}) {
  const params = await searchParams;
  return (
    <AuthFlow
      mode="verify"
      token={queryValue(params.token)}
      nextPath={queryValue(params.next)}
    />
  );
}
